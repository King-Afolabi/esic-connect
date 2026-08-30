import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  JUSTIFICATION_CATEGORIES,
  MyAttendanceDetail as MyAttendanceDetailDto,
  justificationCategoryLabel,
  justificationStatusLabel,
} from '../attendance.models';
import { attendanceStatusLabel, correctionActionLabel, formatInstantUtc } from '../../sessions/sessions.models';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; detail: MyAttendanceDetailDto };

/** Détail d'une présence de l'apprenant : faits, historique de correction et justificatif. */
@Component({
  selector: 'app-my-attendance-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-attendance-detail.html',
  styleUrl: './my-attendance-list.scss',
})
export class MyAttendanceDetail {
  private readonly api = inject(AttendanceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  protected readonly categories = JUSTIFICATION_CATEGORIES;
  protected readonly categoryLabel = justificationCategoryLabel;
  protected readonly justificationStatusLabel = justificationStatusLabel;
  protected readonly attendanceStatusLabel = attendanceStatusLabel;
  protected readonly correctionActionLabel = correctionActionLabel;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly editing = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly amendForm = this.fb.nonNullable.group({
    category: ['MEDICAL', Validators.required],
    externalReference: ['', Validators.maxLength(120)],
    comment: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  protected readonly isStudentContext = computed(() =>
    this.roleContext.effectiveRoles().includes('STUDENT'),
  );
  protected readonly detail = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.detail : null;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });
  protected readonly canAmend = computed(
    () => this.isStudentContext() && this.detail()?.justification?.status === 'PENDING',
  );

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected startEdit(): void {
    const j = this.detail()?.justification;
    if (!j) {
      return;
    }
    this.amendForm.reset({
      category: j.category,
      externalReference: j.externalReference ?? '',
      comment: j.comment,
    });
    this.formError.set(null);
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.formError.set(null);
  }

  protected submitEdit(): void {
    const j = this.detail()?.justification;
    if (!j || this.amendForm.invalid || this.submitting() || !this.canAmend()) {
      this.amendForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    const raw = this.amendForm.getRawValue();
    this.api
      .amendJustification(j.publicId, {
        category: raw.category as never,
        externalReference: raw.externalReference.trim() || null,
        comment: raw.comment.trim(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.editing.set(false);
          this.notifications.info('Justificatif mis à jour.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.formError.set(toAttendanceError(error).message);
        },
      });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.getMyAttendance(this.id).subscribe({
      next: (detail) => this.state.set({ kind: 'ready', detail }),
      error: (error: unknown) => {
        const view = toAttendanceError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        this.state.set(view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
      },
    });
  }
}
