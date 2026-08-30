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
  ASSIGNMENT_SORT_FIELDS,
  AlternationContextResponse,
  AssignmentSortField,
  ClassAssignmentResponse,
  SortDirection,
  WorkStudyPatternResponse,
  alternationContextLabel,
  classPatternStatusLabel,
  contextSourceLabel,
  formatAssignmentPeriod,
  formatIsoDate,
  weekdayLabel,
  workStudyPatternTypeLabel,
} from '../../alternation.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'not-found' }
  | { kind: 'ready'; assignments: ClassAssignmentResponse[] };

type ContextState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; result: AlternationContextResponse };

const DEFAULT_SORT_FIELD: AssignmentSortField = 'validFrom';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';

/**
 * Gestion du rythme d'alternance d'une **classe** :
 * - historique des affectations (`GET .../classes/{id}/assignments`) ;
 * - affectation d'un rythme (`POST .../class-assignments`) ;
 * - clôture d'une affectation en vigueur (`POST .../{id}/close`) ;
 * - résolution du contexte de la classe à une date
 *   (`GET .../classes/{id}/context?date=…`).
 *
 * Aucune date saisie n'est corrigée silencieusement ; les conflits
 * (`400 ALT_INVALID_PERIOD`, `409 ALT_ASSIGNMENT_OVERLAP` /
 * `ALT_OPEN_ASSIGNMENT_EXISTS` / `ALT_ASSIGNMENT_CLOSE_CONFLICT`,
 * `403 ALT_FORBIDDEN`…) sont affichés tels quels. Les contrôles côté
 * client servent l'ergonomie, pas la garantie contre les chevauchements
 * concurrents — le back-end reste l'autorité.
 */
@Component({
  selector: 'app-class-alternation',
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
  templateUrl: './class-alternation.html',
  styleUrl: './class-alternation.scss',
})
export class ClassAlternation {
  private readonly api = inject(AlternationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly classPublicId = this.route.snapshot.paramMap.get('classPublicId') ?? '';

  protected readonly typeLabel = workStudyPatternTypeLabel;
  protected readonly statusLabel = classPatternStatusLabel;
  protected readonly contextLabel = alternationContextLabel;
  protected readonly sourceLabel = contextSourceLabel;
  protected readonly weekdayLabel = weekdayLabel;
  protected readonly formatIsoDate = formatIsoDate;
  protected readonly formatPeriod = formatAssignmentPeriod;
  protected readonly historyColumns = [
    'pattern',
    'type',
    'cycleStartDate',
    'period',
    'status',
    'actions',
  ] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<AssignmentSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);

  /** Modèles ACTIVE proposés dans le formulaire d'affectation. */
  protected readonly activePatterns = signal<WorkStudyPatternResponse[]>([]);

  protected readonly assignSubmitting = signal(false);
  protected readonly assignError = signal<string | null>(null);
  protected readonly assignForm = this.formBuilder.group({
    workStudyPatternPublicId: this.formBuilder.control('', [Validators.required]),
    cycleStartDate: this.formBuilder.control('', [Validators.required]),
    validFrom: this.formBuilder.control('', [Validators.required]),
    validUntil: this.formBuilder.control(''),
  });

  /** Affectation dont le panneau de clôture est ouvert. */
  protected readonly closingId = signal<string | null>(null);
  protected readonly closeSubmitting = signal(false);
  protected readonly closeError = signal<string | null>(null);
  protected readonly closeForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(500)]),
    effectiveDate: this.formBuilder.control(''),
  });

  protected readonly contextForm = this.formBuilder.group({
    date: this.formBuilder.control('', [Validators.required]),
  });
  protected readonly contextState = signal<ContextState>({ kind: 'idle' });

  protected readonly assignments = computed<ClassAssignmentResponse[]>(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.assignments : [];
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly classCode = computed<string | null>(() => {
    const rows = this.assignments();
    return rows.find((a) => a.classGroupCode)?.classGroupCode ?? null;
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
    this.loadAssignments();
    this.loadActivePatterns();
  }

  protected onSortChange(sort: Sort): void {
    const field = ASSIGNMENT_SORT_FIELDS.includes(sort.active as AssignmentSortField)
      ? (sort.active as AssignmentSortField)
      : DEFAULT_SORT_FIELD;
    this.sortField.set(field);
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.loadAssignments();
  }

  protected retry(): void {
    this.loadAssignments();
  }

  protected submitAssign(): void {
    this.assignError.set(null);
    if (this.assignForm.invalid || this.assignSubmitting()) {
      this.assignForm.markAllAsTouched();
      return;
    }
    const raw = this.assignForm.getRawValue();
    this.assignSubmitting.set(true);
    this.api
      .assignClass({
        classGroupPublicId: this.classPublicId,
        workStudyPatternPublicId: raw.workStudyPatternPublicId,
        cycleStartDate: raw.cycleStartDate,
        validFrom: raw.validFrom,
        validUntil: raw.validUntil ? raw.validUntil : null,
      })
      .subscribe({
        next: () => {
          this.assignSubmitting.set(false);
          this.assignForm.reset({
            workStudyPatternPublicId: '',
            cycleStartDate: '',
            validFrom: '',
            validUntil: '',
          });
          this.notifications.info('Rythme affecté à la classe.');
          this.loadAssignments();
        },
        error: (error: unknown) => {
          this.assignSubmitting.set(false);
          this.assignError.set(toAlternationError(error).message);
        },
      });
  }

  protected startClose(assignment: ClassAssignmentResponse): void {
    this.closeForm.reset({ reason: '', effectiveDate: '' });
    this.closeError.set(null);
    this.closingId.set(assignment.publicId);
  }

  protected cancelClose(): void {
    this.closingId.set(null);
    this.closeError.set(null);
  }

  protected submitClose(): void {
    const target = this.closingId();
    this.closeError.set(null);
    if (!target || this.closeForm.invalid || this.closeSubmitting()) {
      this.closeForm.markAllAsTouched();
      return;
    }
    const raw = this.closeForm.getRawValue();
    this.closeSubmitting.set(true);
    this.api
      .closeAssignment(target, {
        reason: raw.reason.trim(),
        effectiveDate: raw.effectiveDate ? raw.effectiveDate : null,
      })
      .subscribe({
        next: () => {
          this.closeSubmitting.set(false);
          this.closingId.set(null);
          this.notifications.info('Affectation clôturée.');
          this.loadAssignments();
        },
        error: (error: unknown) => {
          this.closeSubmitting.set(false);
          this.closeError.set(toAlternationError(error).message);
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
    this.api.getClassContext(this.classPublicId, date).subscribe({
      next: (result) => this.contextState.set({ kind: 'ready', result }),
      error: (error: unknown) =>
        this.contextState.set({ kind: 'error', message: toAlternationError(error).message }),
    });
  }

  private loadAssignments(): void {
    this.state.set({ kind: 'loading' });
    this.api
      .listAssignmentsByClass(this.classPublicId, {
        sort: `${this.sortField()},${this.sortDirection()}`,
        size: 100,
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', assignments: page.content }),
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

  private loadActivePatterns(): void {
    this.api.listPatterns({ status: 'ACTIVE', sort: 'code,asc', size: 100 }).subscribe({
      next: (page) => this.activePatterns.set(page.content),
      error: () => this.activePatterns.set([]),
    });
  }
}
