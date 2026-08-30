import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { COMMON_TIME_ZONES, zonedWallTimeToInstant } from '../../alternation/zoned-time';
import { NotificationService } from '../../../core/notifications/notification.service';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import { TeacherOptionResponse, teacherName } from '../sessions.models';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready' };

/** Motif d'une séance exceptionnelle — `@NotBlank @Size(max = 500)`. */
const REASON_MAX_LENGTH = 500;
const TITLE_MAX_LENGTH = 191;

/**
 * Création d'une séance exceptionnelle — `POST /api/v1/sessions`.
 *
 * Réservée à `ADMIN` / `SUPER_ADMIN` / `PEDAGOGICAL_MANAGER` (garde de
 * route + `@PreAuthorize`). Le formateur et les classes sont choisis dans
 * des listes alimentées par des endpoints réels
 * (`GET /api/v1/sessions/teachers`, `GET /api/v1/class-groups`) — aucun
 * catalogue inventé, aucune saisie d'identifiant SQL. Le motif est
 * obligatoire. La validation temporelle locale est indicative ; le
 * back-end reste l'autorité.
 */
@Component({
  selector: 'app-session-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './session-form.html',
  styleUrl: './session-form.scss',
})
export class SessionForm {
  private readonly api = inject(SessionsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly timeZones = COMMON_TIME_ZONES;
  protected readonly teacherName = teacherName;
  protected readonly reasonMaxLength = REASON_MAX_LENGTH;
  protected readonly titleMaxLength = TITLE_MAX_LENGTH;

  protected readonly loadState = signal<LoadState>({ kind: 'loading' });
  protected readonly teachers = signal<TeacherOptionResponse[]>([]);
  protected readonly classes = signal<ClassGroupResponse[]>([]);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly timeError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    teacherPublicId: this.formBuilder.control('', [Validators.required]),
    classPublicIds: this.formBuilder.control<string[]>([], [Validators.required]),
    date: this.formBuilder.control('', [Validators.required]),
    startTime: this.formBuilder.control('08:00', [Validators.required]),
    endTime: this.formBuilder.control('12:00', [Validators.required]),
    timeZoneId: this.formBuilder.control('Europe/Paris', [Validators.required]),
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(REASON_MAX_LENGTH),
    ]),
    title: this.formBuilder.control('', [Validators.maxLength(TITLE_MAX_LENGTH)]),
  });

  protected readonly reasonLength = computed(() => this.form.controls.reason.value.trim().length);

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected submit(): void {
    this.submitError.set(null);
    this.timeError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const startsAt = zonedWallTimeToInstant(`${raw.date}T${raw.startTime}`, raw.timeZoneId);
    const endsAt = zonedWallTimeToInstant(`${raw.date}T${raw.endTime}`, raw.timeZoneId);
    if (!startsAt || !endsAt) {
      this.timeError.set('Date, heures ou fuseau horaire invalides.');
      return;
    }
    if (new Date(endsAt).getTime() <= new Date(startsAt).getTime()) {
      this.timeError.set('La fin de la séance doit être postérieure à son début.');
      return;
    }

    this.submitting.set(true);
    this.api
      .createSession({
        teacherPublicId: raw.teacherPublicId,
        classPublicIds: raw.classPublicIds,
        startsAt,
        endsAt,
        timeZoneId: raw.timeZoneId,
        reason: raw.reason.trim(),
        title: raw.title.trim() || null,
      })
      .subscribe({
        next: (session) => {
          this.submitting.set(false);
          this.notifications.info('Séance créée.');
          void this.router.navigate(['/sessions', session.publicId]);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.submitError.set(toSessionError(error).message);
        },
      });
  }

  private load(): void {
    this.loadState.set({ kind: 'loading' });
    forkJoin({
      teachers: this.api.listEligibleTeachers(),
      classes: this.academic.listClassGroups({ status: 'ACTIVE', size: 100, sort: 'code,asc' }),
    }).subscribe({
      next: ({ teachers, classes }) => {
        this.teachers.set(teachers);
        this.classes.set(classes.content);
        this.loadState.set({ kind: 'ready' });
      },
      error: (error: unknown) => {
        const view = toSessionError(error);
        this.loadState.set(
          view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
        );
      },
    });
  }
}
