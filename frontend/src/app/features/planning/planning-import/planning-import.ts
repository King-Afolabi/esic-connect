import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { NotificationService } from '../../../core/notifications/notification.service';
import { PlanningApiService } from '../planning-api.service';
import { toPlanningError } from '../planning-errors';

type ClassesState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; classes: ClassGroupResponse[] };

const ACCEPTED_EXTENSION = '.csv';
const MAX_BYTES = 2 * 1024 * 1024;

/**
 * Téléversement d'un CSV de planning pour une classe (EF-PLAN-001). Le
 * fichier n'est ni lu ni parsé côté navigateur : il est transmis brut au
 * back-end, qui lance une **simulation** (aucune séance créée — AC-007).
 * On redirige alors vers la revue du job.
 *
 * La liste des classes vient de `GET /api/v1/class-groups` (lecture
 * ouverte aux mêmes rôles) ; un `PEDAGOGICAL_MANAGER` n'y voit que son
 * périmètre, et un `403` de l'API de simulation reste rendu « accès
 * refusé ».
 */
@Component({
  selector: 'app-planning-import',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './planning-import.html',
  styleUrl: './planning-import.scss',
})
export class PlanningImport {
  private readonly api = inject(PlanningApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly acceptedExtension = ACCEPTED_EXTENSION;

  protected readonly classesState = signal<ClassesState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly fileError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control('', [Validators.required]),
  });

  protected readonly classes = computed<ClassGroupResponse[]>(() => {
    const current = this.classesState();
    return current.kind === 'ready' ? current.classes : [];
  });
  protected readonly classesError = computed(() => {
    const current = this.classesState();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.loadClasses();
  }

  protected loadClasses(): void {
    this.classesState.set({ kind: 'loading' });
    this.academic.listClassGroups({ status: 'ACTIVE', sort: 'code,asc', size: 100 }).subscribe({
      next: (page) => this.classesState.set({ kind: 'ready', classes: page.content }),
      error: (error: unknown) =>
        this.classesState.set({ kind: 'error', message: toPlanningError(error).message }),
    });
  }

  protected onFileSelected(event: Event): void {
    this.fileError.set(null);
    this.submitError.set(null);
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      this.selectedFile.set(null);
      return;
    }
    if (!file.name.toLowerCase().endsWith(ACCEPTED_EXTENSION)) {
      this.fileError.set('Seuls les fichiers CSV (.csv) sont acceptés.');
      this.selectedFile.set(null);
      return;
    }
    if (file.size > MAX_BYTES) {
      this.fileError.set('Le fichier dépasse la taille maximale autorisée (2 Mo).');
      this.selectedFile.set(null);
      return;
    }
    this.selectedFile.set(file);
  }

  protected submit(): void {
    const file = this.selectedFile();
    this.submitError.set(null);
    if (this.form.invalid || !file || this.submitting()) {
      this.form.markAllAsTouched();
      if (!file) {
        this.fileError.set('Sélectionnez un fichier CSV.');
      }
      return;
    }
    this.submitting.set(true);
    this.api.simulate(file, this.form.getRawValue().classGroupPublicId).subscribe({
      next: (job) => {
        this.submitting.set(false);
        this.notifications.info('Simulation lancée.');
        void this.router.navigate(['/planning/import', job.publicId]);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.submitError.set(toPlanningError(error).message);
      },
    });
  }
}
