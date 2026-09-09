import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { PlanningApiService } from '../planning-api.service';
import { toPlanningError } from '../planning-errors';
import {
  PlanningCalendarSlot,
  PlanningCalendarView,
  PlanningJobResponse,
} from '../planning.models';

type ClassesState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; classes: ClassGroupResponse[] };

type CalendarState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; view: PlanningCalendarView };

/**
 * Construction directe d'un planning dans un calendrier (EF-PLAN-006 ;
 * docs/02 §13.7).
 *
 * <p>L'écran n'invente aucune règle : il ajoute, déplace, supprime,
 * duplique et répète des créneaux dans un **brouillon**, et affiche ce que
 * le serveur renvoie — anomalies comprises. Le brouillon est un travail
 * d'import ordinaire ; sa publication se fait par l'écran de revue, avec
 * les mêmes contrôles de conflit et le même versionnement atomique.
 *
 * <p>Le publié et le brouillon sont présentés **séparément** : les
 * mélanger laisserait croire qu'un créneau non publié fait déjà foi.
 */
@Component({
  selector: 'app-planning-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './planning-calendar.html',
  styleUrl: './planning-calendar.scss',
})
export class PlanningCalendar {
  private readonly api = inject(PlanningApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly slotColumns = ['day', 'window', 'title', 'room', 'status', 'actions'];
  protected readonly publishedColumns = ['day', 'window', 'title', 'room'];

  protected readonly classesState = signal<ClassesState>({ kind: 'loading' });
  protected readonly calendarState = signal<CalendarState>({ kind: 'idle' });
  protected readonly job = signal<PlanningJobResponse | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editedRowId = signal<string | null>(null);

  protected readonly filters = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control(''),
    from: this.formBuilder.control(firstDayOfCurrentMonth()),
    to: this.formBuilder.control(lastDayOfCurrentMonth()),
  });

  /**
   * Saisie d'un créneau. `timeZoneId` est un champ à part entière : un
   * planning sans fuseau explicite se décale silencieusement au
   * changement d'heure.
   */
  protected readonly slotForm = this.formBuilder.group({
    sessionDate: this.formBuilder.control('', Validators.required),
    startTime: this.formBuilder.control('09:00', Validators.required),
    endTime: this.formBuilder.control('12:30', Validators.required),
    timeZoneId: this.formBuilder.control('Europe/Paris', Validators.required),
    title: this.formBuilder.control('', Validators.required),
    teacherPublicId: this.formBuilder.control(''),
    roomCode: this.formBuilder.control(''),
  });

  protected readonly weekForm = this.formBuilder.group({
    sourceWeekStart: this.formBuilder.control(''),
    targetWeekStart: this.formBuilder.control(''),
  });

  protected readonly classes = computed<ClassGroupResponse[]>(() => {
    const current = this.classesState();
    return current.kind === 'ready' ? current.classes : [];
  });
  protected readonly classesError = computed(() => {
    const current = this.classesState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly calendarErrorMessage = computed(() => {
    const current = this.calendarState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly published = computed<PlanningCalendarSlot[]>(() => {
    const current = this.calendarState();
    return current.kind === 'ready' ? current.view.published : [];
  });
  protected readonly draft = computed<PlanningCalendarSlot[]>(() => {
    const current = this.calendarState();
    return current.kind === 'ready' ? current.view.draft : [];
  });
  protected readonly draftJobId = computed<string | null>(() => {
    const current = this.calendarState();
    return current.kind === 'ready' ? current.view.draftJobPublicId : null;
  });
  protected readonly publishedVersionNumber = computed<number | null>(() => {
    const current = this.calendarState();
    return current.kind === 'ready' ? current.view.publishedVersionNumber : null;
  });

  constructor() {
    this.loadClasses();
    this.filters.controls.classGroupPublicId.valueChanges.subscribe((value) => {
      this.job.set(null);
      this.actionError.set(null);
      this.editedRowId.set(null);
      if (value) {
        this.loadCalendar();
      } else {
        this.calendarState.set({ kind: 'idle' });
      }
    });
  }

  protected loadClasses(): void {
    this.classesState.set({ kind: 'loading' });
    this.academic.listClassGroups({ status: 'ACTIVE', sort: 'code,asc', size: 100 }).subscribe({
      next: (page) => this.classesState.set({ kind: 'ready', classes: page.content }),
      error: (error: unknown) =>
        this.classesState.set({ kind: 'error', message: toPlanningError(error).message }),
    });
  }

  protected loadCalendar(): void {
    const { classGroupPublicId, from, to } = this.filters.getRawValue();
    if (!classGroupPublicId || !from || !to) {
      return;
    }
    this.calendarState.set({ kind: 'loading' });
    this.api.calendar(classGroupPublicId, from, to).subscribe({
      next: (view) => this.calendarState.set({ kind: 'ready', view }),
      error: (error: unknown) => {
        const view = toPlanningError(error);
        this.calendarState.set(
          view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
        );
      },
    });
  }

  protected addSlot(): void {
    const classId = this.filters.getRawValue().classGroupPublicId;
    if (!classId || this.slotForm.invalid || this.busy()) {
      this.slotForm.markAllAsTouched();
      return;
    }
    this.run(this.api.addSlot(classId, this.slotValues()));
  }

  /** Prépare le formulaire pour déplacer ou modifier un créneau existant. */
  protected editSlot(slot: PlanningCalendarSlot): void {
    this.editedRowId.set(slot.publicId);
    this.slotForm.patchValue({
      sessionDate: slot.day,
      startTime: this.slotForm.getRawValue().startTime,
      endTime: this.slotForm.getRawValue().endTime,
      timeZoneId: slot.timeZoneId ?? 'Europe/Paris',
      title: slot.title ?? '',
      teacherPublicId: slot.teacherPublicId ?? '',
      roomCode: slot.roomCode ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editedRowId.set(null);
  }

  protected saveEdit(): void {
    const jobId = this.draftJobId();
    const rowId = this.editedRowId();
    if (!jobId || !rowId || this.slotForm.invalid || this.busy()) {
      this.slotForm.markAllAsTouched();
      return;
    }
    this.run(this.api.updateSlot(jobId, rowId, this.slotValues()), () => this.editedRowId.set(null));
  }

  protected removeSlot(slot: PlanningCalendarSlot): void {
    const jobId = this.draftJobId();
    if (!jobId || this.busy()) {
      return;
    }
    this.run(this.api.removeSlot(jobId, slot.publicId));
  }

  protected repeatSlot(slot: PlanningCalendarSlot): void {
    const jobId = this.draftJobId();
    if (!jobId || this.busy()) {
      return;
    }
    this.run(this.api.repeatSlot(jobId, slot.publicId, 3, 7));
  }

  protected duplicateWeek(): void {
    const jobId = this.draftJobId();
    const { sourceWeekStart, targetWeekStart } = this.weekForm.getRawValue();
    if (!jobId || !sourceWeekStart || !targetWeekStart || this.busy()) {
      return;
    }
    this.run(this.api.duplicateWeek(jobId, sourceWeekStart, targetWeekStart));
  }

  protected slotWindow(slot: PlanningCalendarSlot): string {
    if (!slot.startsAt || !slot.endsAt) {
      return '—';
    }
    return `${timeOf(slot.startsAt, slot.timeZoneId)} – ${timeOf(slot.endsAt, slot.timeZoneId)}`;
  }

  private slotValues() {
    const raw = this.slotForm.getRawValue();
    return {
      sessionDate: raw.sessionDate,
      startTime: raw.startTime,
      endTime: raw.endTime,
      timeZoneId: raw.timeZoneId,
      title: raw.title,
      teacherPublicId: raw.teacherPublicId || null,
      roomCode: raw.roomCode || null,
    };
  }

  /**
   * Toute mutation renvoie le **travail réanalysé** : les conflits de
   * planning sont croisés, il n'y a donc rien à recalculer côté client.
   * On recharge ensuite le calendrier, seul à connaître la fenêtre
   * affichée.
   */
  private run(
    call: import('rxjs').Observable<PlanningJobResponse>,
    onSuccess?: () => void,
  ): void {
    this.busy.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: (job) => {
        this.job.set(job);
        this.busy.set(false);
        onSuccess?.();
        this.loadCalendar();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.actionError.set(toPlanningError(error).message);
      },
    });
  }
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function firstDayOfCurrentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-01`;
}

function lastDayOfCurrentMonth(): string {
  const now = new Date();
  const last = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return `${last.getFullYear()}-${pad(last.getMonth() + 1)}-${pad(last.getDate())}`;
}

/** Heure locale du créneau dans SON fuseau, jamais celui du navigateur. */
function timeOf(instant: string, timeZoneId: string | null): string {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  try {
    return new Intl.DateTimeFormat('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: timeZoneId ?? 'Europe/Paris',
    }).format(date);
  } catch {
    return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
  }
}
