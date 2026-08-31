import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import { frenchPaginatorIntl } from '../../../alternation/alternation-paginator';
import { RoleContextService } from '../../../../core/auth/role-context.service';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { StudentImportApiService } from '../student-import-api.service';
import { toStudentImportError } from '../student-import-errors';
import { JobResponse, MAX_CSV_BYTES, jobStatusLabel } from '../student-import.models';

const WRITE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const;

type SubmitState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'error'; message: string; details: string[] };

/**
 * Écran d'accueil de l'import CSV des apprenants (rapport §13.2) :
 * téléversement d'un fichier `.csv`, options de périmètre facultatives,
 * lancement de la **simulation**, et liste des imports récents.
 *
 * Le fichier est transmis brut (`FormData`) et n'est jamais lu côté
 * navigateur. Les contrôles client (extension, taille) ne sont qu'une
 * aide : le serveur reste l'autorité. Un `403` de l'API est rendu comme
 * un message « périmètre » ; un `5xx` comme un message générique — jamais
 * le corps brut.
 */
@Component({
  selector: 'app-student-import-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './student-import-home.html',
  styleUrl: './student-import-home.scss',
})
export class StudentImportHome {
  private readonly api = inject(StudentImportApiService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly roleContext = inject(RoleContextService);

  protected readonly statusLabel = jobStatusLabel;
  protected readonly maxBytes = MAX_CSV_BYTES;
  protected readonly displayedColumns = ['fileName', 'status', 'summary', 'createdAt', 'actions'] as const;

  /** Vrai si le contexte de rôle actif permet de lancer un import. */
  protected readonly canImport = computed(() =>
    this.roleContext.effectiveRoles().some((r) => (WRITE_ROLES as readonly string[]).includes(r)),
  );

  protected readonly scope = this.formBuilder.group({
    programCode: this.formBuilder.control(''),
    classCode: this.formBuilder.control(''),
  });

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly fileError = signal<string | null>(null);
  protected readonly submit = signal<SubmitState>({ kind: 'idle' });

  protected readonly recentJobs = signal<JobResponse[]>([]);
  protected readonly recentError = signal<string | null>(null);

  protected readonly canSubmit = computed(
    () =>
      this.canImport() &&
      this.selectedFile() !== null &&
      this.fileError() === null &&
      this.submit().kind !== 'submitting',
  );

  protected readonly errorMessage = computed(() => {
    const state = this.submit();
    return state.kind === 'error' ? state.message : null;
  });
  protected readonly errorDetails = computed(() => {
    const state = this.submit();
    return state.kind === 'error' ? state.details : [];
  });

  constructor() {
    this.loadRecent();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.submit.set({ kind: 'idle' });
    if (!file) {
      this.selectedFile.set(null);
      this.fileError.set(null);
      return;
    }
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.selectedFile.set(null);
      this.fileError.set('Seuls les fichiers CSV (extension .csv) sont acceptés.');
      return;
    }
    if (file.size > this.maxBytes) {
      this.selectedFile.set(null);
      this.fileError.set('Le fichier dépasse 2 Mo.');
      return;
    }
    this.fileError.set(null);
    this.selectedFile.set(file);
  }

  protected launch(): void {
    const file = this.selectedFile();
    if (!file || !this.canSubmit()) {
      return;
    }
    this.submit.set({ kind: 'submitting' });
    const scope = this.scope.getRawValue();
    this.api.simulate(file, scope.programCode, scope.classCode).subscribe({
      next: (job) => {
        if (!this.canImport()) {
          return; // contexte de rôle perdu pendant l'appel : réponse ignorée
        }
        void this.router.navigate(['/students/import', job.publicId]);
      },
      error: (error: unknown) => {
        if (!this.canImport()) {
          return;
        }
        const view = toStudentImportError(error);
        this.submit.set({
          kind: 'error',
          message: view.forbidden
            ? "Le périmètre demandé n'est pas dans votre périmètre pédagogique."
            : view.message,
          details: view.details,
        });
      },
    });
  }

  private loadRecent(): void {
    this.api.listJobs({ sort: 'createdAt,desc', page: 0, size: 10 }).subscribe({
      next: (page) => this.recentJobs.set(page.content),
      error: (error: unknown) => this.recentError.set(toStudentImportError(error).message),
    });
  }
}
