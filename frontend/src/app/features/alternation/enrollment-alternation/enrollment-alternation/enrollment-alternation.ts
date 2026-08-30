import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { AlternationApiService } from '../../alternation-api.service';
import { toAlternationError } from '../../alternation-errors';
import {
  EXCEPTION_SORT_FIELDS,
  EnrollmentContextResponse,
  ExceptionSortField,
  SCHEDULE_EXCEPTION_TYPES,
  SCHEDULE_EXCEPTION_TYPE_EFFECT,
  SortDirection,
  StudentExceptionResponse,
  alternationContextLabel,
  contextSourceLabel,
  formatInstantUtc,
  formatIsoDate,
  scheduleExceptionStatusLabel,
  scheduleExceptionTypeLabel,
} from '../../alternation.models';
import { COMMON_TIME_ZONES, isSupportedTimeZone, zonedWallTimeToInstant } from '../../zoned-time';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'not-found' }
  | { kind: 'ready'; exceptions: StudentExceptionResponse[] };

type ContextState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; result: EnrollmentContextResponse };

const DEFAULT_SORT_FIELD: ExceptionSortField = 'startAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';

/**
 * Exceptions individuelles d'une **inscription** et contexte effectif à
 * une date.
 *
 * Sémantique temporelle affichée explicitement : une exception couvre
 * l'intervalle **`[startAt, endAt)`** — début inclus, fin exclue. Le
 * formulaire recueille une heure locale + un fuseau IANA ; l'instant
 * absolu transmis est calculé par simple encodage
 * ({@link zonedWallTimeToInstant}) sans jamais convertir vers un autre
 * fuseau ni retomber sur UTC si le fuseau est inconnu. Le back-end reste
 * l'autorité (`400 ALT_INVALID_TIME_ZONE`, `400 ALT_INVALID_PERIOD`,
 * `409 ALT_EXCEPTION_OVERLAP`, `403 ALT_FORBIDDEN`…). Le contexte
 * effectif provient exclusivement de l'endpoint serveur.
 */
@Component({
  selector: 'app-enrollment-alternation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatTableModule,
    MatSortModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './enrollment-alternation.html',
  styleUrl: './enrollment-alternation.scss',
})
export class EnrollmentAlternation {
  private readonly api = inject(AlternationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly enrollmentPublicId =
    this.route.snapshot.paramMap.get('enrollmentPublicId') ?? '';

  protected readonly exceptionTypes = SCHEDULE_EXCEPTION_TYPES;
  protected readonly timeZones = COMMON_TIME_ZONES;
  protected readonly typeLabel = scheduleExceptionTypeLabel;
  protected readonly typeEffect = (type: string): string =>
    (SCHEDULE_EXCEPTION_TYPE_EFFECT as Record<string, string>)[type] ?? '';
  protected readonly statusLabel = scheduleExceptionStatusLabel;
  protected readonly contextLabel = alternationContextLabel;
  protected readonly sourceLabel = contextSourceLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly formatIsoDate = formatIsoDate;
  protected readonly columns = [
    'type',
    'start',
    'end',
    'timeZone',
    'reason',
    'status',
    'actions',
  ] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<ExceptionSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);

  protected readonly createSubmitting = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly createForm = this.formBuilder.group({
    type: this.formBuilder.control(SCHEDULE_EXCEPTION_TYPES[0], [Validators.required]),
    startWall: this.formBuilder.control('', [Validators.required]),
    endWall: this.formBuilder.control('', [Validators.required]),
    timeZoneId: this.formBuilder.control('Europe/Paris', [Validators.required]),
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(500)]),
  });
  private readonly createTick = signal(0);

  protected readonly cancellingId = signal<string | null>(null);
  protected readonly cancelSubmitting = signal(false);
  protected readonly cancelError = signal<string | null>(null);
  protected readonly cancelForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(500)]),
  });

  protected readonly contextForm = this.formBuilder.group({
    date: this.formBuilder.control('', [Validators.required]),
  });
  protected readonly contextState = signal<ContextState>({ kind: 'idle' });

  protected readonly exceptions = computed<StudentExceptionResponse[]>(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.exceptions : [];
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  /** Instants absolus déduits de la saisie (heure locale + fuseau). */
  protected readonly computedInstants = computed(() => {
    this.createTick();
    const raw = this.createForm.getRawValue();
    const zoneOk = isSupportedTimeZone(raw.timeZoneId);
    const startAt = zonedWallTimeToInstant(raw.startWall, raw.timeZoneId);
    const endAt = zonedWallTimeToInstant(raw.endWall, raw.timeZoneId);
    const endAfterStart =
      startAt !== null && endAt !== null ? new Date(endAt) > new Date(startAt) : null;
    return { zoneOk, startAt, endAt, endAfterStart };
  });
  protected readonly createBlockingMessage = computed<string | null>(() => {
    const raw = this.createForm.getRawValue();
    const { zoneOk, startAt, endAt, endAfterStart } = this.computedInstants();
    if (raw.timeZoneId && !zoneOk) {
      return 'Fuseau horaire inconnu. Choisissez un identifiant IANA valide.';
    }
    if (raw.startWall && startAt === null) {
      return 'Date/heure de début invalide.';
    }
    if (raw.endWall && endAt === null) {
      return 'Date/heure de fin invalide.';
    }
    if (endAfterStart === false) {
      return 'La fin doit être strictement postérieure au début.';
    }
    return null;
  });

  protected readonly contextResult = computed(() => {
    const current = this.contextState();
    return current.kind === 'ready' ? current.result : null;
  });
  protected readonly contextError = computed(() => {
    const current = this.contextState();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.loadExceptions();
    this.createForm.valueChanges.subscribe(() => this.createTick.update((n) => n + 1));
  }

  protected onSortChange(sort: Sort): void {
    const field = EXCEPTION_SORT_FIELDS.includes(sort.active as ExceptionSortField)
      ? (sort.active as ExceptionSortField)
      : DEFAULT_SORT_FIELD;
    this.sortField.set(field);
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.loadExceptions();
  }

  protected retry(): void {
    this.loadExceptions();
  }

  protected submitCreate(): void {
    this.createError.set(null);
    const { startAt, endAt } = this.computedInstants();
    if (
      this.createForm.invalid ||
      this.createSubmitting() ||
      this.createBlockingMessage() !== null ||
      startAt === null ||
      endAt === null
    ) {
      this.createForm.markAllAsTouched();
      return;
    }
    const raw = this.createForm.getRawValue();
    this.createSubmitting.set(true);
    this.api
      .createException({
        enrollmentPublicId: this.enrollmentPublicId,
        type: raw.type,
        startAt,
        endAt,
        timeZoneId: raw.timeZoneId.trim(),
        reason: raw.reason.trim(),
      })
      .subscribe({
        next: () => {
          this.createSubmitting.set(false);
          this.createForm.reset({
            type: SCHEDULE_EXCEPTION_TYPES[0],
            startWall: '',
            endWall: '',
            timeZoneId: 'Europe/Paris',
            reason: '',
          });
          this.notifications.info('Exception créée.');
          this.loadExceptions();
        },
        error: (error: unknown) => {
          this.createSubmitting.set(false);
          this.createError.set(toAlternationError(error).message);
        },
      });
  }

  protected startCancel(exception: StudentExceptionResponse): void {
    this.cancelForm.reset({ reason: '' });
    this.cancelError.set(null);
    this.cancellingId.set(exception.publicId);
  }

  protected dismissCancel(): void {
    this.cancellingId.set(null);
    this.cancelError.set(null);
  }

  protected submitCancel(): void {
    const target = this.cancellingId();
    this.cancelError.set(null);
    if (!target || this.cancelForm.invalid || this.cancelSubmitting()) {
      this.cancelForm.markAllAsTouched();
      return;
    }
    this.cancelSubmitting.set(true);
    this.api
      .cancelException(target, { reason: this.cancelForm.getRawValue().reason.trim() })
      .subscribe({
        next: () => {
          this.cancelSubmitting.set(false);
          this.cancellingId.set(null);
          this.notifications.info('Exception annulée.');
          this.loadExceptions();
        },
        error: (error: unknown) => {
          this.cancelSubmitting.set(false);
          this.cancelError.set(toAlternationError(error).message);
        },
      });
  }

  protected resolveContext(): void {
    if (this.contextForm.invalid) {
      this.contextForm.markAllAsTouched();
      return;
    }
    const date = this.contextForm.getRawValue().date;
    this.contextState.set({ kind: 'loading' });
    this.api.getEnrollmentContext(this.enrollmentPublicId, date).subscribe({
      next: (result) => this.contextState.set({ kind: 'ready', result }),
      error: (error: unknown) =>
        this.contextState.set({ kind: 'error', message: toAlternationError(error).message }),
    });
  }

  private loadExceptions(): void {
    this.state.set({ kind: 'loading' });
    this.api
      .listExceptionsByEnrollment(this.enrollmentPublicId, {
        sort: `${this.sortField()},${this.sortDirection()}`,
        size: 100,
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', exceptions: page.content }),
        error: (error: unknown) => {
          const view = toAlternationError(error);
          if (view.notFound) {
            this.state.set({ kind: 'not-found' });
            return;
          }
          this.state.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }
}
