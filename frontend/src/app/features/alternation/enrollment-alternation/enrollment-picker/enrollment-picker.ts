import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AcademicApiService } from '../../../academic/academic-api.service';
import { ClassGroupResponse } from '../../../academic/academic.models';
import { EnrollmentResponse } from '../../../students/students.models';
import { StudentsApiService } from '../../../students/students-api.service';
import { toAlternationError } from '../../alternation-errors';
import { formatAssignmentPeriod } from '../../alternation.models';

type ClassState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; classes: ClassGroupResponse[] };

type EnrollmentState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; enrollments: EnrollmentResponse[] };

/**
 * Sélection d'une inscription pour la gestion de ses exceptions
 * individuelles de calendrier.
 *
 * Parcours : chercher une classe (`GET /api/v1/class-groups`) → lister
 * ses inscriptions (`GET /api/v1/enrollments?classGroup=…`).
 *
 * **Limite back-end connue** : `GET /api/v1/enrollments` est réservé à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` (`EnrollmentWeb.MANAGE_ROLES`) ;
 * le `PEDAGOGICAL_MANAGER`, pourtant autorisé sur les exceptions
 * d'alternance dans son périmètre, ne peut pas encore parcourir les
 * inscriptions. Pour ce cas, un champ de saisie directe de
 * l'identifiant d'inscription reste disponible.
 */
@Component({
  selector: 'app-enrollment-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './enrollment-picker.html',
  styleUrl: './enrollment-picker.scss',
})
export class EnrollmentPicker {
  private readonly academic = inject(AcademicApiService);
  private readonly students = inject(StudentsApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly formatPeriod = formatAssignmentPeriod;
  protected readonly classColumns = ['code', 'name', 'actions'] as const;
  protected readonly enrollmentColumns = ['studentNumber', 'period', 'status', 'actions'] as const;

  protected readonly classSearch = this.formBuilder.group({
    q: this.formBuilder.control(''),
  });
  protected readonly manualForm = this.formBuilder.group({
    enrollmentPublicId: this.formBuilder.control('', [Validators.required]),
  });

  protected readonly classState = signal<ClassState>({ kind: 'idle' });
  protected readonly enrollmentState = signal<EnrollmentState>({ kind: 'idle' });
  protected readonly selectedClass = signal<ClassGroupResponse | null>(null);

  protected readonly classes = computed<ClassGroupResponse[]>(() => {
    const current = this.classState();
    return current.kind === 'ready' ? current.classes : [];
  });
  protected readonly classError = computed(() => {
    const current = this.classState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly enrollments = computed<EnrollmentResponse[]>(() => {
    const current = this.enrollmentState();
    return current.kind === 'ready' ? current.enrollments : [];
  });
  protected readonly enrollmentError = computed(() => {
    const current = this.enrollmentState();
    return current.kind === 'error' ? current.message : null;
  });

  protected searchClasses(): void {
    this.classState.set({ kind: 'loading' });
    this.academic
      .listClassGroups({
        q: this.classSearch.getRawValue().q.trim() || null,
        sort: 'code,asc',
        size: 20,
      })
      .subscribe({
        next: (page) => this.classState.set({ kind: 'ready', classes: page.content }),
        error: (error: unknown) => {
          const view = toAlternationError(error);
          this.classState.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }

  protected selectClass(classGroup: ClassGroupResponse): void {
    this.selectedClass.set(classGroup);
    this.enrollmentState.set({ kind: 'loading' });
    this.students
      .listEnrollments({ classGroup: classGroup.publicId, sort: 'startDate,desc', size: 100 })
      .subscribe({
        next: (page) => this.enrollmentState.set({ kind: 'ready', enrollments: page.content }),
        error: (error: unknown) => {
          const view = toAlternationError(error);
          this.enrollmentState.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }

  protected openManual(): void {
    if (this.manualForm.invalid) {
      this.manualForm.markAllAsTouched();
      return;
    }
    const id = this.manualForm.getRawValue().enrollmentPublicId.trim();
    void this.router.navigate(['/alternation/enrollments', id]);
  }
}
