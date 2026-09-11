import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AcademicApiService } from '../../academic/academic-api.service';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { normalizeHttpError } from '../../../core/models/api-error';
import { StudentsApiService } from '../students-api.service';
import {
  EnrollmentResponse,
  RemoteAttendanceAuthorizationResponse,
  StudentProfileResponse,
  UserIdentitySummary,
  enrollmentSourceLabel,
  enrollmentStatusLabel,
  remoteAuthorizationStatusLabel,
  studentProfileStatusLabel,
} from '../students.models';

/** Rôles habilités à changer la classe / clôturer une inscription (`EnrollmentWeb.MANAGE_ROLES`). */
const ENROLLMENT_WRITE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const;

type ProfileState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; profile: StudentProfileResponse; identity: UserIdentitySummary | null };

type HistoryState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; enrollments: EnrollmentResponse[] };

type RemoteState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; authorizations: RemoteAttendanceAuthorizationResponse[] };

/**
 * Fiche d'un apprenant et historique de ses inscriptions.
 *
 * - `GET /api/v1/student-profiles/{publicId}` : le profil ;
 * - `GET /api/v1/enrollments?student={publicId}&sort=startDate,desc` :
 *   l'historique (RG-006, RG-023, AC-006 — l'ancienne inscription reste
 *   consultable après un changement de classe) ;
 * - `GET /api/v1/users/{userPublicId}` : identité civile, **facultative**
 *   (le profil n'expose que `userPublicId`). Un échec est ignoré.
 *
 * Un `404` sur le profil rend un état « introuvable » ; un `403` rend un
 * état « accès refusé » (le contrôle d'accès reste côté Spring Security).
 */
@Component({
  selector: 'app-student-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './student-profile.html',
  styleUrl: './student-profile.scss',
})
export class StudentProfile {
  private readonly api = inject(StudentsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = studentProfileStatusLabel;
  protected readonly enrollmentStatusLabel = enrollmentStatusLabel;
  protected readonly enrollmentSourceLabel = enrollmentSourceLabel;
  protected readonly historyColumns = [
    'academicYear',
    'classGroup',
    'program',
    'period',
    'status',
    'source',
  ] as const;

  protected readonly remoteStatusLabel = remoteAuthorizationStatusLabel;
  protected readonly remoteColumns = ['period', 'scope', 'reason', 'status', 'actions'] as const;

  protected readonly state = signal<ProfileState>({ kind: 'loading' });
  protected readonly history = signal<HistoryState>({ kind: 'loading' });
  protected readonly remote = signal<RemoteState>({ kind: 'loading' });
  protected readonly remoteBusy = signal(false);
  protected readonly remoteActionError = signal<string | null>(null);

  /**
   * « Changer de classe » (docs/04 §13.2) — typiquement un passage en
   * année supérieure : l'inscription courante est clôturée en
   * `TRANSFERRED` et une nouvelle s'ouvre dans la classe cible, sans
   * perdre l'historique (RG-006, RG-023).
   */
  protected readonly canManageEnrollment = computed(() =>
    this.roleContext.effectiveRoles().some((r) => (ENROLLMENT_WRITE_ROLES as readonly string[]).includes(r)),
  );
  protected readonly transferOpen = signal(false);
  protected readonly transferBusy = signal(false);
  protected readonly transferError = signal<string | null>(null);
  protected readonly classOptions = signal<{ publicId: string; code: string }[]>([]);
  protected readonly classesState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  protected readonly transferForm = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control('', Validators.required),
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(500)]),
    effectiveDate: this.formBuilder.control(''),
  });

  /**
   * Octroi d'une autorisation de suivi à distance (EF-ENR-004).
   * `classGroupPublicId` est laissé vide pour une autorisation générale —
   * que le serveur réserve à un périmètre global.
   */
  protected readonly remoteForm = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control(''),
    reason: this.formBuilder.control('', Validators.required),
    validFrom: this.formBuilder.control('', Validators.required),
    validUntil: this.formBuilder.control(''),
  });

  protected readonly profile = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.profile : null;
  });
  protected readonly identity = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.identity : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly historyRows = computed<EnrollmentResponse[]>(() => {
    const current = this.history();
    return current.kind === 'ready' ? current.enrollments : [];
  });

  /**
   * Scolarité actuelle — <strong>dérivée</strong> de l'historique déjà
   * chargé (`GET /api/v1/enrollments?student=…&sort=startDate,desc`),
   * aucun appel supplémentaire, aucun N+1. On retient l'inscription
   * `ACTIVE` ; à défaut, la plus récente (la liste est triée par date de
   * début décroissante). `null` tant que l'historique n'est pas prêt ou
   * qu'aucune inscription n'existe.
   *
   * <p>Le contrat `EnrollmentResponse` porte des <em>codes</em> lisibles
   * (`classGroupCode`, `programCode`, `academicYearCode`) — jamais des
   * UUID. Le niveau, la promotion et le rythme d'alternance n'y figurent
   * pas : ils ne sont pas affichés plutôt qu'inventés.</p>
   */
  protected readonly currentEnrollment = computed<EnrollmentResponse | null>(() => {
    const rows = this.historyRows();
    if (rows.length === 0) {
      return null;
    }
    return rows.find((row) => row.status === 'ACTIVE') ?? rows[0];
  });
  protected readonly historyError = computed(() => {
    const current = this.history();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly historyEmpty = computed(() => {
    const current = this.history();
    return current.kind === 'ready' && current.enrollments.length === 0;
  });
  protected readonly remoteRows = computed<RemoteAttendanceAuthorizationResponse[]>(() => {
    const current = this.remote();
    return current.kind === 'ready' ? current.authorizations : [];
  });
  protected readonly remoteError = computed(() => {
    const current = this.remote();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.loadProfile();
  }

  protected retryProfile(): void {
    this.loadProfile();
  }

  protected retryHistory(): void {
    this.loadHistory();
  }

  protected retryRemote(): void {
    const profile = this.profile();
    if (profile) {
      this.loadRemote(profile.userPublicId);
    }
  }

  protected grantRemote(): void {
    const profile = this.profile();
    if (!profile || this.remoteForm.invalid || this.remoteBusy()) {
      this.remoteForm.markAllAsTouched();
      return;
    }
    const raw = this.remoteForm.getRawValue();
    this.remoteBusy.set(true);
    this.remoteActionError.set(null);
    this.api
      .authorizeRemoteAttendance({
        studentUserPublicId: profile.userPublicId,
        classGroupPublicId: raw.classGroupPublicId || null,
        reason: raw.reason,
        validFrom: raw.validFrom,
        validUntil: raw.validUntil || null,
      })
      .subscribe({
        next: () => {
          this.remoteBusy.set(false);
          this.remoteForm.reset({
            classGroupPublicId: '',
            reason: '',
            validFrom: '',
            validUntil: '',
          });
          this.loadRemote(profile.userPublicId);
        },
        error: (error: unknown) => {
          this.remoteBusy.set(false);
          this.remoteActionError.set(normalizeHttpError(error).message);
        },
      });
  }

  protected revokeRemote(authorization: RemoteAttendanceAuthorizationResponse): void {
    const profile = this.profile();
    if (!profile || this.remoteBusy()) {
      return;
    }
    this.remoteBusy.set(true);
    this.remoteActionError.set(null);
    this.api
      .revokeRemoteAttendance(authorization.publicId, 'Retrait de l’autorisation')
      .subscribe({
        next: () => {
          this.remoteBusy.set(false);
          this.loadRemote(profile.userPublicId);
        },
        error: (error: unknown) => {
          this.remoteBusy.set(false);
          this.remoteActionError.set(normalizeHttpError(error).message);
        },
      });
  }

  protected startTransfer(): void {
    if (!this.canManageEnrollment()) {
      return;
    }
    this.transferOpen.set(true);
    this.transferError.set(null);
    this.transferForm.reset({ classGroupPublicId: '', reason: '', effectiveDate: '' });
    if (this.classesState() === 'idle') {
      this.loadClassOptions();
    }
  }

  protected cancelTransfer(): void {
    this.transferOpen.set(false);
    this.transferError.set(null);
  }

  protected submitTransfer(): void {
    const enrollment = this.currentEnrollment();
    if (!enrollment || this.transferForm.invalid || this.transferBusy() || !this.canManageEnrollment()) {
      this.transferForm.markAllAsTouched();
      return;
    }
    const raw = this.transferForm.getRawValue();
    this.transferBusy.set(true);
    this.transferError.set(null);
    this.api
      .transferEnrollment(enrollment.publicId, {
        classGroupPublicId: raw.classGroupPublicId,
        reason: raw.reason.trim(),
        effectiveDate: raw.effectiveDate || null,
      })
      .subscribe({
        next: () => {
          this.transferBusy.set(false);
          this.transferOpen.set(false);
          this.loadHistory();
        },
        error: (error: unknown) => {
          this.transferBusy.set(false);
          this.transferError.set(normalizeHttpError(error).message);
        },
      });
  }

  private loadClassOptions(): void {
    this.classesState.set('loading');
    this.academic.listClassGroups({ status: 'ACTIVE', size: 200, sort: 'code,asc' }).subscribe({
      next: (page) => {
        this.classOptions.set(page.content.map((c) => ({ publicId: c.publicId, code: c.code })));
        this.classesState.set('ready');
      },
      error: () => this.classesState.set('error'),
    });
  }

  private loadProfile(): void {
    this.state.set({ kind: 'loading' });
    this.api.getProfile(this.publicId).subscribe({
      next: (profile) => {
        this.state.set({ kind: 'ready', profile, identity: null });
        this.loadIdentity(profile.userPublicId);
        this.loadHistory();
        this.loadRemote(profile.userPublicId);
      },
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        if (normalized.status === 404) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (normalized.status === 403) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: normalized.message });
      },
    });
  }

  /** Facultatif : n'altère jamais l'état du profil en cas d'échec. */
  private loadIdentity(userPublicId: string): void {
    this.api.getUserIdentity(userPublicId).subscribe({
      next: (identity) => {
        const current = this.state();
        if (current.kind === 'ready') {
          this.state.set({ ...current, identity });
        }
      },
      error: () => {
        /* identité indisponible : la fiche reste affichée sans nom civil */
      },
    });
  }

  /**
   * Autorisations de suivi à distance. Un `403` n'est pas une erreur : le
   * rôle courant n'a simplement pas à décider de ces autorisations, la
   * section est alors masquée plutôt que présentée en échec.
   */
  private loadRemote(userPublicId: string): void {
    this.remote.set({ kind: 'loading' });
    this.api.listRemoteAuthorizations(userPublicId).subscribe({
      next: (authorizations) => this.remote.set({ kind: 'ready', authorizations }),
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        this.remote.set(
          normalized.status === 403
            ? { kind: 'forbidden' }
            : { kind: 'error', message: normalized.message },
        );
      },
    });
  }

  private loadHistory(): void {
    this.history.set({ kind: 'loading' });
    this.api
      .listEnrollments({ student: this.publicId, sort: 'startDate,desc', size: 100 })
      .subscribe({
        next: (page) => this.history.set({ kind: 'ready', enrollments: page.content }),
        error: (error: unknown) =>
          this.history.set({ kind: 'error', message: normalizeHttpError(error).message }),
      });
  }
}
