import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
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
import { OrganizationApiService } from '../../organization/organization-api.service';
import { RoomResponse } from '../../organization/organization.models';
import { SubjectsApiService } from '../../subjects/subjects-api.service';
import { SubjectResponse } from '../../subjects/subjects.models';
import { COMMON_TIME_ZONES, zonedWallTimeToInstant } from '../../alternation/zoned-time';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import {
  SESSION_CREATE_ROLES,
  TeacherOptionResponse,
  holdsAnySessionRole,
  teacherName,
} from '../sessions.models';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'permission-lost' }
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
 *
 * Si le **contexte de rôle actif** cesse de permettre la création (l'utilisateur
 * bascule vers un contexte plus restreint), le formulaire est neutralisé
 * immédiatement (désactivé, panneau « permission perdue ») et toute
 * soumission — y compris une réponse arrivée tardivement — est ignorée.
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
  private readonly subjectsApi = inject(SubjectsApiService);
  private readonly organizationApi = inject(OrganizationApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly roleContext = inject(RoleContextService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** Le contexte de rôle actif autorise-t-il encore la création ? */
  protected readonly canCreate = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_CREATE_ROLES),
  );

  protected readonly timeZones = COMMON_TIME_ZONES;
  protected readonly teacherName = teacherName;
  protected readonly reasonMaxLength = REASON_MAX_LENGTH;
  protected readonly titleMaxLength = TITLE_MAX_LENGTH;

  protected readonly loadState = signal<LoadState>({ kind: 'loading' });
  protected readonly teachers = signal<TeacherOptionResponse[]>([]);
  protected readonly classes = signal<ClassGroupResponse[]>([]);
  protected readonly subjects = signal<SubjectResponse[]>([]);
  /** Salles du site déduit des classes choisies ; vide tant qu'aucune classe n'est sélectionnée. */
  protected readonly rooms = signal<RoomResponse[]>([]);
  protected readonly roomsLoading = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly timeError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    teacherPublicId: this.formBuilder.control('', [Validators.required]),
    // Facultatives (V35) : une matière et une salle non précisées restent
    // valides — la salle, en particulier, est souvent décidée au dernier
    // moment et peut être affectée plus tard depuis la fiche de la séance.
    subjectPublicId: this.formBuilder.control(''),
    roomPublicId: this.formBuilder.control(''),
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

    // Perte de permission via le contexte de rôle : neutralise le
    // formulaire sensible et bloque toute nouvelle soumission.
    effect(() => {
      if (!this.canCreate()) {
        this.form.disable({ emitEvent: false });
        this.submitting.set(false);
        this.submitError.set(null);
        this.timeError.set(null);
        this.loadState.set({ kind: 'permission-lost' });
      }
    });

    // Les salles sont rattachées à un site (`GET /sites/{id}/rooms`) : on
    // recharge la liste dès qu'une classe rattachée à un site différent
    // est choisie, plutôt que de faire porter un site au formulaire.
    this.form.controls.classPublicIds.valueChanges.subscribe((ids) => this.loadRoomsFor(ids));
  }

  private loadRoomsFor(classPublicIds: string[]): void {
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
          // La salle choisie n'appartient plus au site déduit : on la vide
          // plutôt que de soumettre une valeur incohérente.
          if (!page.content.some((room) => room.publicId === this.form.controls.roomPublicId.value)) {
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
    // Le contexte de rôle actif ne permet plus la création : aucune requête.
    if (!this.canCreate()) {
      return;
    }
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
        subjectPublicId: raw.subjectPublicId || null,
        roomPublicId: raw.roomPublicId || null,
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
          // Réponse tardive après une perte de permission : on l'ignore.
          if (!this.canCreate()) {
            return;
          }
          this.notifications.info('Séance créée.');
          void this.router.navigate(['/sessions', session.publicId]);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (!this.canCreate()) {
            return;
          }
          this.submitError.set(toSessionError(error).message);
        },
      });
  }

  private load(): void {
    this.loadState.set({ kind: 'loading' });
    forkJoin({
      teachers: this.api.listEligibleTeachers(),
      classes: this.academic.listClassGroups({ status: 'ACTIVE', size: 100, sort: 'code,asc' }),
      subjects: this.subjectsApi.list({ status: 'ACTIVE', size: 200, sort: 'name,asc' }),
    }).subscribe({
      next: ({ teachers, classes, subjects }) => {
        this.teachers.set(teachers);
        this.classes.set(classes.content);
        this.subjects.set(subjects.content);
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
