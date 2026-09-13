import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { OrganizationApiService } from '../../organization/organization-api.service';
import { RoomResponse } from '../../organization/organization.models';
import { SubjectsApiService } from '../../subjects/subjects-api.service';
import { SubjectResponse } from '../../subjects/subjects.models';
import { instantToZonedWallParts, zonedWallTimeToInstant } from '../../alternation/zoned-time';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import {
  CourseSessionResponse,
  SESSION_ATTENDANCE_MODES,
  SESSION_CREATE_ROLES,
  SessionAttendanceMode,
  TeacherOptionResponse,
  holdsAnySessionRole,
  isValidRemoteLink,
  sessionAttendanceModeLabel,
  sessionStatusLabel,
  teacherName,
} from '../sessions.models';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'not-found' }
  | { kind: 'not-editable'; status: string }
  | { kind: 'permission-lost' }
  | { kind: 'ready' };

const REASON_MAX_LENGTH = 500;
const TITLE_MAX_LENGTH = 191;
const REMOTE_LINK_MAX_LENGTH = 500;

/**
 * Cohérence modalité / lien distant (Lot 11, reprise à l'édition) : voir
 * `session-form.ts` pour la règle complète. Dupliquée volontairement —
 * deux formulaires distincts, pas d'état partagé entre eux.
 */
function remoteLinkValidator(control: AbstractControl): ValidationErrors | null {
  const mode = control.parent?.get('attendanceMode')?.value as SessionAttendanceMode | undefined;
  const value = (control.value as string).trim();
  if (mode === 'REMOTE' && !value) {
    return { required: true };
  }
  if (value && !isValidRemoteLink(value)) {
    return { invalidUrl: true };
  }
  return null;
}

/**
 * Édition structurelle complète d'une séance exceptionnelle (Lot 12 ;
 * docs/02 §14.6) — `PATCH /api/v1/sessions/{publicId}`.
 *
 * Réservée aux séances `PLANNED` non démarrées (le back-end est
 * l'autorité : `SESSION_INVALID_STATE` si l'état ne le permet plus,
 * revérifié à la soumission). Le fuseau horaire de la séance n'est
 * jamais modifiable ici (docs/02 §14.6 règle 10) : les horaires édités
 * restent interprétés selon ce fuseau, affiché en lecture seule.
 */
@Component({
  selector: 'app-session-edit',
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
  templateUrl: './session-edit.html',
  styleUrl: './session-edit.scss',
})
export class SessionEdit {
  private readonly api = inject(SessionsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly subjectsApi = inject(SubjectsApiService);
  private readonly organizationApi = inject(OrganizationApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly roleContext = inject(RoleContextService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  /** Fuseau déclaré de la séance : jamais modifiable, jamais celui du navigateur. */
  private sessionTimeZoneId = 'Europe/Paris';

  protected readonly canEdit = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_CREATE_ROLES),
  );

  protected readonly statusLabel = sessionStatusLabel;
  protected readonly reasonMaxLength = REASON_MAX_LENGTH;
  protected readonly titleMaxLength = TITLE_MAX_LENGTH;
  protected readonly remoteLinkMaxLength = REMOTE_LINK_MAX_LENGTH;
  protected readonly teacherName = teacherName;
  protected readonly attendanceModes = SESSION_ATTENDANCE_MODES.map((value) => ({
    value,
    label: sessionAttendanceModeLabel(value),
  }));

  protected readonly loadState = signal<LoadState>({ kind: 'loading' });
  protected readonly teachers = signal<TeacherOptionResponse[]>([]);
  protected readonly classes = signal<ClassGroupResponse[]>([]);
  protected readonly subjects = signal<SubjectResponse[]>([]);
  protected readonly rooms = signal<RoomResponse[]>([]);
  protected readonly roomsLoading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly timeError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    teacherPublicId: this.formBuilder.control('', [Validators.required]),
    subjectPublicId: this.formBuilder.control(''),
    roomPublicId: this.formBuilder.control(''),
    classPublicIds: this.formBuilder.control<string[]>([], [Validators.required]),
    date: this.formBuilder.control('', [Validators.required]),
    startTime: this.formBuilder.control('08:00', [Validators.required]),
    endTime: this.formBuilder.control('12:00', [Validators.required]),
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(REASON_MAX_LENGTH),
    ]),
    title: this.formBuilder.control('', [Validators.maxLength(TITLE_MAX_LENGTH)]),
    attendanceMode: this.formBuilder.control<SessionAttendanceMode>('ON_SITE'),
    remoteLink: this.formBuilder.control('', [
      Validators.maxLength(REMOTE_LINK_MAX_LENGTH),
      remoteLinkValidator,
    ]),
  });

  protected readonly reasonLength = computed(() => this.form.controls.reason.value.trim().length);
  protected readonly attendanceMode = signal<SessionAttendanceMode>('ON_SITE');

  constructor() {
    this.load();

    effect(() => {
      if (!this.canEdit()) {
        this.form.disable({ emitEvent: false });
        this.submitting.set(false);
        this.submitError.set(null);
        this.timeError.set(null);
        this.loadState.set({ kind: 'permission-lost' });
      }
    });

    this.form.controls.classPublicIds.valueChanges.subscribe((ids) => this.loadRoomsFor(ids, null));

    this.form.controls.attendanceMode.valueChanges.subscribe((mode) => {
      this.attendanceMode.set(mode);
      if (mode === 'ON_SITE') {
        this.form.controls.remoteLink.setValue('');
      }
      this.form.controls.remoteLink.updateValueAndValidity();
    });
  }

  private loadRoomsFor(classPublicIds: string[], preselectRoomCode: string | null): void {
    const site = classPublicIds
      .map((id) => this.classes().find((c) => c.publicId === id)?.sitePublicId)
      .find((value): value is string => !!value);
    if (!site) {
      this.rooms.set([]);
      this.form.controls.roomPublicId.setValue('');
      return;
    }
    this.roomsLoading.set(true);
    this.organizationApi
      .listRooms(site, { status: 'ACTIVE', size: 200, sort: 'code,asc' })
      .subscribe({
        next: (page) => {
          this.roomsLoading.set(false);
          this.rooms.set(page.content);
          const preselected = preselectRoomCode
            ? page.content.find((room) => room.code === preselectRoomCode)
            : undefined;
          if (preselected) {
            this.form.controls.roomPublicId.setValue(preselected.publicId);
          } else if (!page.content.some((room) => room.publicId === this.form.controls.roomPublicId.value)) {
            this.form.controls.roomPublicId.setValue('');
          }
        },
        error: () => {
          this.roomsLoading.set(false);
          this.rooms.set([]);
        },
      });
  }

  protected retry(): void {
    this.load();
  }

  protected submit(): void {
    this.submitError.set(null);
    this.timeError.set(null);
    if (!this.canEdit()) {
      return;
    }
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const startsAt = zonedWallTimeToInstant(`${raw.date}T${raw.startTime}`, this.sessionTimeZoneId);
    const endsAt = zonedWallTimeToInstant(`${raw.date}T${raw.endTime}`, this.sessionTimeZoneId);
    if (!startsAt || !endsAt) {
      this.timeError.set('Date ou heures invalides.');
      return;
    }
    if (new Date(endsAt).getTime() <= new Date(startsAt).getTime()) {
      this.timeError.set('La fin de la séance doit être postérieure à son début.');
      return;
    }

    this.submitting.set(true);
    this.api
      .updateSession(this.publicId, {
        teacherPublicId: raw.teacherPublicId,
        subjectPublicId: raw.subjectPublicId || null,
        roomPublicId: raw.roomPublicId || null,
        classPublicIds: raw.classPublicIds,
        startsAt,
        endsAt,
        reason: raw.reason.trim(),
        title: raw.title.trim() || null,
        attendanceMode: raw.attendanceMode,
        remoteLink: raw.remoteLink.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          if (!this.canEdit()) {
            return;
          }
          this.notifications.info('Séance modifiée.');
          void this.router.navigate(['/sessions', this.publicId]);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (!this.canEdit()) {
            return;
          }
          this.submitError.set(toSessionError(error).message);
        },
      });
  }

  private load(): void {
    this.loadState.set({ kind: 'loading' });
    forkJoin({
      session: this.api.getSession(this.publicId),
      teachers: this.api.listEligibleTeachers(),
      classes: this.academic.listClassGroups({ status: 'ACTIVE', size: 100, sort: 'code,asc' }),
      subjects: this.subjectsApi.list({ status: 'ACTIVE', size: 200, sort: 'name,asc' }),
    }).subscribe({
      next: ({ session, teachers, classes, subjects }) => {
        this.teachers.set(teachers);
        this.classes.set(classes.content);
        this.subjects.set(subjects.content);
        if (session.status !== 'PLANNED') {
          this.loadState.set({ kind: 'not-editable', status: session.status });
          return;
        }
        this.applySession(session);
        this.loadState.set({ kind: 'ready' });
      },
      error: (error: unknown) => {
        const view = toSessionError(error);
        this.loadState.set(
          view.forbidden
            ? { kind: 'forbidden' }
            : view.notFound
              ? { kind: 'not-found' }
              : { kind: 'error', message: view.message },
        );
      },
    });
  }

  private applySession(session: CourseSessionResponse): void {
    this.sessionTimeZoneId = session.timeZoneId;
    this.attendanceMode.set(session.attendanceMode);
    const start = instantToZonedWallParts(session.startsAt, session.timeZoneId);
    const end = instantToZonedWallParts(session.endsAt, session.timeZoneId);
    this.form.patchValue(
      {
        teacherPublicId: session.teacher.publicId ?? '',
        subjectPublicId: session.subject?.publicId ?? '',
        classPublicIds: session.classes.map((c) => c.publicId),
        date: start?.date ?? '',
        startTime: start?.time ?? '',
        endTime: end?.time ?? '',
        reason: session.exceptionReason,
        title: session.title ?? '',
        attendanceMode: session.attendanceMode,
        remoteLink: session.remoteLink ?? '',
      },
      { emitEvent: false },
    );
    this.loadRoomsFor(this.form.controls.classPublicIds.value, session.roomCode);
  }

  protected get timeZoneDisplay(): string {
    return this.sessionTimeZoneId;
  }
}
