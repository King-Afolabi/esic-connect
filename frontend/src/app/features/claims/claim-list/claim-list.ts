import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { formatInstantUtc } from '../../sessions/sessions.models';
import { ClaimsApiService } from '../claims-api.service';
import { toClaimError } from '../claim-errors';
import {
  CLAIM_AUDIENCES,
  CLAIM_CATEGORIES,
  CLAIM_STAFF_ROLES,
  ClaimSessionOption,
  ClaimSummary,
  ClaimTeacherOption,
  claimAudienceLabel,
  claimCategoryLabel,
  claimStatusLabel,
  claimTeacherOptionLabel,
} from '../claims.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: ClaimSummary[]; total: number };

/** Pas de recherche en dessous de deux caractères : un déluge de résultats n'aiderait personne. */
const MIN_QUERY_LENGTH = 2;

const NO_SESSION_TEACHER_WARNING =
  "Aucune séance n'est sélectionnée. La réclamation sera transmise au responsable pédagogique.";

/**
 * Réclamations : liste et dépôt (EF-CLAIM-001 ; docs/02 §20).
 *
 * <p>La même route sert l'apprenant et les intervenants — le serveur
 * décide de ce que chacun voit à partir de son rôle et de son périmètre.
 * Le front n'ajoute aucun filtre d'autorisation : il ne ferait
 * qu'imiter une décision déjà prise, et divergerait au premier
 * changement.
 *
 * <p>Le destinataire est un <strong>guichet</strong>, pas une personne :
 * c'est ce qui permet à une réclamation d'être transférée sans perdre son
 * histoire.
 */
@Component({
  selector: 'app-claim-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatProgressBarModule,
  ],
  templateUrl: './claim-list.html',
  styleUrl: './claim-list.scss',
})
export class ClaimList {
  private readonly api = inject(ClaimsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly categories = CLAIM_CATEGORIES;
  protected readonly audiences = CLAIM_AUDIENCES;
  protected readonly categoryLabel = claimCategoryLabel;
  protected readonly audienceLabel = claimAudienceLabel;
  protected readonly statusLabel = claimStatusLabel;
  protected readonly teacherOptionLabel = claimTeacherOptionLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = [
    'subject',
    'author',
    'category',
    'audience',
    'status',
    'updatedAt',
    'open',
  ] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly page = signal(0);
  private readonly size = 20;
  protected readonly creating = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  /**
   * Recherche assistée (Lot 18/19) : l'utilisateur tape un texte libre
   * (`sessionQuery` / `teacherQuery`) ; seule une sélection dans les
   * suggestions renseigne l'identifiant réellement soumis
   * (`sessionPublicId` / `targetTeacherPublicId`). Aucun préchargement —
   * chaque frappe interroge le serveur, borné et débattu.
   */
  protected readonly sessionOptions = signal<ClaimSessionOption[]>([]);
  protected readonly teacherOptions = signal<ClaimTeacherOption[]>([]);
  /** Reflets signal des contrôles pertinents : `computed` ne suit pas un `FormControl`. */
  protected readonly audienceValue = signal('PEDAGOGICAL_MANAGER');
  protected readonly selectedSessionId = signal('');
  protected readonly selectedTeacherId = signal('');

  protected readonly filters = this.fb.nonNullable.group({ audience: [''] });
  protected readonly form = this.fb.nonNullable.group({
    category: ['ATTENDANCE', Validators.required],
    audience: ['PEDAGOGICAL_MANAGER', Validators.required],
    subject: ['', [Validators.required, Validators.maxLength(191)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]],
    sessionQuery: [''],
    sessionPublicId: [''],
    teacherQuery: [''],
    targetTeacherPublicId: [''],
  });

  /** Le guichet TEACHER seul propose le ciblage facultatif d'un formateur (Lot 19). */
  protected readonly showTeacherTargeting = computed(() => this.audienceValue() === 'TEACHER');

  /**
   * Avertissement de repli (Lot 19, décision 3.c) : uniquement quand le
   * guichet est TEACHER, sans formateur choisi ni séance sélectionnée.
   * Un formateur choisi, ou une séance choisie, n'affiche rien — ce
   * n'est pas une erreur, juste une information de résolution.
   */
  protected readonly noTeacherOrSessionWarning = computed(() => {
    if (this.audienceValue() !== 'TEACHER') {
      return null;
    }
    if (this.selectedTeacherId() || this.selectedSessionId()) {
      return null;
    }
    return NO_SESSION_TEACHER_WARNING;
  });

  /**
   * Le dépôt est ouvert à tout compte authentifié. Un intervenant qui
   * dépose une réclamation le fait au même titre que n'importe qui —
   * le serveur choisit le guichet par défaut selon son rôle.
   */
  protected readonly isStaff = computed(() =>
    this.roleContext.effectiveRoles().some((role) => CLAIM_STAFF_ROLES.includes(role as never)),
  );

  protected readonly totalPages = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? Math.max(1, Math.ceil(current.total / this.size)) : 1;
  });

  constructor() {
    this.load();

    this.form.controls.audience.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.audienceValue.set(value));

    // Une sélection reste valide tant que le texte affiché n'a pas
    // changé sous elle : retaper efface la sélection précédente, pour ne
    // jamais soumettre un identifiant qui ne correspond plus au texte
    // affiché à l'écran.
    this.form.controls.sessionQuery.valueChanges
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        distinctUntilChanged(),
        debounceTime(300),
        switchMap((query) => {
          if (this.form.controls.sessionPublicId.value) {
            this.form.controls.sessionPublicId.setValue('');
            this.selectedSessionId.set('');
          }
          return query.trim().length >= MIN_QUERY_LENGTH ? this.api.searchSessions(query) : of([]);
        }),
      )
      .subscribe((options) => this.sessionOptions.set(options));

    this.form.controls.teacherQuery.valueChanges
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        distinctUntilChanged(),
        debounceTime(300),
        switchMap((query) => {
          if (this.form.controls.targetTeacherPublicId.value) {
            this.form.controls.targetTeacherPublicId.setValue('');
            this.selectedTeacherId.set('');
          }
          return query.trim().length >= MIN_QUERY_LENGTH ? this.api.searchTeachers(query) : of([]);
        }),
      )
      .subscribe((options) => this.teacherOptions.set(options));
  }

  protected selectSession(option: ClaimSessionOption): void {
    this.form.controls.sessionPublicId.setValue(option.publicId);
    this.form.controls.sessionQuery.setValue(option.label, { emitEvent: false });
    this.selectedSessionId.set(option.publicId);
    this.sessionOptions.set([]);
  }

  protected selectTeacher(option: ClaimTeacherOption): void {
    this.form.controls.targetTeacherPublicId.setValue(option.publicId);
    this.form.controls.teacherQuery.setValue(this.teacherOptionLabel(option), { emitEvent: false });
    this.selectedTeacherId.set(option.publicId);
    this.teacherOptions.set([]);
  }

  protected toggleCreate(): void {
    this.creating.update((open) => !open);
    this.formError.set(null);
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  protected previous(): void {
    this.page.update((p) => Math.max(0, p - 1));
    this.load();
  }

  protected next(): void {
    this.page.update((p) => Math.min(this.totalPages() - 1, p + 1));
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set(null);
    this.api
      .create({
        category: raw.category,
        audience: raw.audience,
        subject: raw.subject.trim(),
        description: raw.description.trim(),
        sessionPublicId: raw.sessionPublicId.trim() || null,
        targetTeacherPublicId: raw.targetTeacherPublicId.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.creating.set(false);
          this.form.reset({
            category: 'ATTENDANCE',
            audience: 'PEDAGOGICAL_MANAGER',
            subject: '',
            description: '',
            sessionQuery: '',
            sessionPublicId: '',
            teacherQuery: '',
            targetTeacherPublicId: '',
          });
          this.audienceValue.set('PEDAGOGICAL_MANAGER');
          this.selectedSessionId.set('');
          this.selectedTeacherId.set('');
          this.sessionOptions.set([]);
          this.teacherOptions.set([]);
          this.notifications.info('Réclamation ouverte.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.formError.set(toClaimError(error).message);
        },
      });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api
      .list({
        audience: this.filters.getRawValue().audience || null,
        page: this.page(),
        size: this.size,
      })
      .subscribe({
        next: (response) =>
          this.state.set({
            kind: 'ready',
            rows: response.content,
            total: response.totalElements,
          }),
        error: (error: unknown) =>
          this.state.set({ kind: 'error', message: toClaimError(error).message }),
      });
  }
}
