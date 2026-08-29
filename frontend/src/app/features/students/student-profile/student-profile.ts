import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { StudentsApiService } from '../students-api.service';
import {
  EnrollmentResponse,
  StudentProfileResponse,
  UserIdentitySummary,
  enrollmentSourceLabel,
  enrollmentStatusLabel,
  studentProfileStatusLabel,
} from '../students.models';

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
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './student-profile.html',
  styleUrl: './student-profile.scss',
})
export class StudentProfile {
  private readonly api = inject(StudentsApiService);
  private readonly route = inject(ActivatedRoute);

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

  protected readonly state = signal<ProfileState>({ kind: 'loading' });
  protected readonly history = signal<HistoryState>({ kind: 'loading' });

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
  protected readonly historyError = computed(() => {
    const current = this.history();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly historyEmpty = computed(() => {
    const current = this.history();
    return current.kind === 'ready' && current.enrollments.length === 0;
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

  private loadProfile(): void {
    this.state.set({ kind: 'loading' });
    this.api.getProfile(this.publicId).subscribe({
      next: (profile) => {
        this.state.set({ kind: 'ready', profile, identity: null });
        this.loadIdentity(profile.userPublicId);
        this.loadHistory();
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
