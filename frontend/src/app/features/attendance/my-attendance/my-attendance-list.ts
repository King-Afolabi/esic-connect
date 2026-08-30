import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  JUSTIFICATION_CATEGORIES,
  MyAttendanceRow,
  justificationCategoryLabel,
  justificationStatusLabel,
} from '../attendance.models';
import { attendanceStatusLabel, formatInstantUtc } from '../../sessions/sessions.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; rows: MyAttendanceRow[]; total: number };

/**
 * Espace « Mes présences » de l'apprenant (V10). Le serveur résout
 * l'apprenant à partir du seul JWT ; aucun identifiant n'est transmis. Le
 * dépôt d'un justificatif est une métadonnée métier (catégorie,
 * référence, commentaire) — aucune pièce jointe dans cette tranche. Rien
 * n'est écrit dans le stockage du navigateur ; aucune donnée sensible en
 * URL.
 */
@Component({
  selector: 'app-my-attendance-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-attendance-list.html',
  styleUrl: './my-attendance-list.scss',
})
export class MyAttendanceList {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);

  protected readonly categories = JUSTIFICATION_CATEGORIES;
  protected readonly categoryLabel = justificationCategoryLabel;
  protected readonly justificationStatusLabel = justificationStatusLabel;
  protected readonly attendanceStatusLabel = attendanceStatusLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = ['session', 'checkpoint', 'status', 'justification', 'actions'] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly page = signal(0);
  private readonly size = 20;

  protected readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: [''],
    status: [''],
  });

  protected readonly justifyRowId = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly justifyForm = this.fb.nonNullable.group({
    category: ['MEDICAL', Validators.required],
    externalReference: ['', Validators.maxLength(120)],
    comment: ['', [Validators.required, Validators.maxLength(1000)]],
  });

  /** L'apprenant est le seul rôle autorisé ; on ferme le formulaire s'il change de contexte. */
  protected readonly isStudentContext = computed(() =>
    this.roleContext.effectiveRoles().includes('STUDENT'),
  );

  protected readonly rows = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.rows : [];
  });
  protected readonly total = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.total : 0;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    this.load();
    // §5 : sortir du contexte STUDENT ferme le formulaire de dépôt et
    // efface le brouillon (catégorie, référence, commentaire, erreur).
    effect(() => {
      if (!this.isStudentContext()) {
        this.justifyRowId.set(null);
        this.formError.set(null);
        this.justifyForm.reset({ category: 'MEDICAL', externalReference: '', comment: '' });
      }
    });
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset({ from: '', to: '', status: '' });
    this.page.set(0);
    this.load();
  }

  protected nextPage(): void {
    if ((this.page() + 1) * this.size < this.total()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }
  protected previousPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  protected retry(): void {
    this.load();
  }

  protected startJustify(row: MyAttendanceRow): void {
    if (!this.isStudentContext()) {
      return;
    }
    this.justifyRowId.set(row.checkpointPublicId);
    this.formError.set(null);
    this.justifyForm.reset({ category: 'MEDICAL', externalReference: '', comment: '' });
  }

  protected cancelJustify(): void {
    this.justifyRowId.set(null);
    this.formError.set(null);
  }

  protected submitJustify(): void {
    const checkpointPublicId = this.justifyRowId();
    if (!checkpointPublicId || this.justifyForm.invalid || this.submitting() || !this.isStudentContext()) {
      this.justifyForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    const raw = this.justifyForm.getRawValue();
    this.api
      .submitJustification({
        checkpointPublicId,
        category: raw.category as never,
        externalReference: raw.externalReference.trim() || null,
        comment: raw.comment.trim(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.justifyRowId.set(null);
          // §5 : contexte STUDENT perdu pendant l'appel — pas de faux
          // succès. Le back-end peut avoir enregistré le dépôt.
          if (!this.isStudentContext()) {
            return;
          }
          this.notifications.info('Justificatif déposé : il sera examiné par l’administration.');
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
    const raw = this.filters.getRawValue();
    this.api
      .listMyAttendance({
        from: isoStart(raw.from),
        to: isoStart(raw.to),
        status: raw.status || null,
        page: this.page(),
        size: this.size,
      })
      .subscribe({
        next: (result) =>
          this.state.set({ kind: 'ready', rows: result.content, total: result.totalElements }),
        error: (error: unknown) => {
          const view = toAttendanceError(error);
          this.state.set(view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
        },
      });
  }
}

/** Convertit une date `YYYY-MM-DD` du filtre en instant ISO (minuit UTC). */
function isoStart(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
