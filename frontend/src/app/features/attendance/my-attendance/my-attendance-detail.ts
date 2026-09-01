import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
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
import { AttendanceApiService, triggerAttachmentDownload } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  JUSTIFICATION_ATTACHMENT_ACCEPT,
  JUSTIFICATION_CATEGORIES,
  JustificationAttachmentMeta,
  MyAttendanceDetail as MyAttendanceDetailDto,
  checkAttachmentFile,
  formatFileSize,
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
  protected readonly formatFileSize = formatFileSize;
  protected readonly attachmentAccept = JUSTIFICATION_ATTACHMENT_ACCEPT;

  // Pièce jointe (bloc G1-E) — 'loading' | 'none' | Meta.
  protected readonly attachment = signal<JustificationAttachmentMeta | 'loading' | 'none'>('loading');
  protected readonly attachmentError = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);
  protected readonly pendingFile = signal<File | null>(null);
  protected readonly downloading = signal(false);
  protected readonly removing = signal(false);

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
  protected readonly attachmentMeta = computed(() => {
    const a = this.attachment();
    return a === 'loading' || a === 'none' ? null : a;
  });

  constructor() {
    this.load();
    // §5 : à la perte du contexte STUDENT, fermer l'édition et effacer le
    // brouillon du justificatif.
    effect(() => {
      if (!this.isStudentContext()) {
        this.editing.set(false);
        this.formError.set(null);
        this.amendForm.reset({ category: 'MEDICAL', externalReference: '', comment: '' });
      }
    });
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
          // §5 : contexte STUDENT perdu pendant l'appel — pas de faux
          // succès. Le back-end peut avoir appliqué la modification.
          if (!this.isStudentContext()) {
            return;
          }
          this.notifications.info('Justificatif mis à jour.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.formError.set(toAttendanceError(error).message);
        },
      });
  }

  // --- Pièce jointe (bloc G1-E) ------------------------------------

  protected onFileSelected(event: Event): void {
    this.uploadError.set(null);
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      this.pendingFile.set(null);
      return;
    }
    const check = checkAttachmentFile(file);
    if (!check.ok) {
      this.pendingFile.set(null);
      this.uploadError.set(check.reason);
      input.value = '';
      return;
    }
    this.pendingFile.set(file);
  }

  protected uploadAttachment(): void {
    const j = this.detail()?.justification;
    const file = this.pendingFile();
    if (!j || !file || this.uploading() || !this.canAmend()) {
      return;
    }
    this.uploading.set(true);
    this.uploadError.set(null);
    this.api.uploadMyJustificationAttachment(j.publicId, file).subscribe({
      next: (meta) => {
        this.uploading.set(false);
        this.pendingFile.set(null);
        if (!this.isStudentContext()) {
          return;
        }
        this.attachment.set(meta);
        this.notifications.info('Pièce jointe déposée.');
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.uploadError.set(toAttendanceError(error).message);
      },
    });
  }

  protected downloadAttachment(): void {
    const j = this.detail()?.justification;
    if (!j || this.downloading()) {
      return;
    }
    this.downloading.set(true);
    this.api.downloadMyJustificationAttachment(j.publicId).subscribe({
      next: (response) => {
        this.downloading.set(false);
        triggerAttachmentDownload(response, this.attachmentMeta()?.fileName ?? 'justificatif');
      },
      error: (error: unknown) => {
        this.downloading.set(false);
        this.attachmentError.set(toAttendanceError(error).message);
      },
    });
  }

  protected removeAttachment(): void {
    const j = this.detail()?.justification;
    if (!j || this.removing() || !this.canAmend()) {
      return;
    }
    this.removing.set(true);
    this.attachmentError.set(null);
    this.api.deleteMyJustificationAttachment(j.publicId).subscribe({
      next: () => {
        this.removing.set(false);
        if (!this.isStudentContext()) {
          return;
        }
        this.attachment.set('none');
        this.notifications.info('Pièce jointe retirée.');
      },
      error: (error: unknown) => {
        this.removing.set(false);
        this.attachmentError.set(toAttendanceError(error).message);
      },
    });
  }

  private loadAttachment(justificationId: string): void {
    this.attachment.set('loading');
    this.attachmentError.set(null);
    this.api.getMyJustificationAttachment(justificationId).subscribe({
      next: (meta) => this.attachment.set(meta),
      error: (error: unknown) => {
        const view = toAttendanceError(error);
        if (view.notFound) {
          this.attachment.set('none');
          return;
        }
        this.attachment.set('none');
        this.attachmentError.set(view.message);
      },
    });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.getMyAttendance(this.id).subscribe({
      next: (detail) => {
        this.state.set({ kind: 'ready', detail });
        if (detail.justification) {
          this.loadAttachment(detail.justification.publicId);
        } else {
          this.attachment.set('none');
        }
      },
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
