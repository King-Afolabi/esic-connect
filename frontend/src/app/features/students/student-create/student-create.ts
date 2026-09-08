import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Observable, map, of, switchMap, tap } from 'rxjs';

import { AcademicApiService } from '../../academic/academic-api.service';
import { normalizeHttpError } from '../../../core/models/api-error';
import { NotificationService } from '../../../core/notifications/notification.service';
import { StudentsApiService } from '../students-api.service';

interface ClassOption {
  publicId: string;
  code: string;
}

/**
 * Création manuelle d'un apprenant (Lot H) — sans passer par un import CSV.
 *
 * Vérifié avant écriture : aucune fonctionnalité de ce type n'existait
 * (le service `students` était en lecture seule pour les profils, et
 * `/students` n'offrait aucune action de création). Ce parcours enchaîne
 * **trois routes existantes**, chacune contrôlée côté serveur :
 *
 *   1. `POST /api/v1/users` — compte `PENDING_ACTIVATION` + invitation
 *      (rôle `STUDENT`, aucun mot de passe) ;
 *   2. `POST /api/v1/student-profiles` — profil (numéro étudiant, etc.) ;
 *   3. `POST /api/v1/enrollments` — inscription initiale dans une classe.
 *
 * L'enchaînement **n'est pas atomique** (trois transactions, deux
 * modules). Ce n'est pas masqué : si une étape échoue, l'écran conserve
 * ce qui a réussi (identifiants du compte / du profil) et **reprend à
 * l'étape fautive** sans rien recréer, en expliquant l'état exact. Une
 * atomicité stricte demanderait un endpoint d'orchestration back-end
 * dédié — signalé comme évolution, pas simulé ici.
 *
 * L'autorisation réelle reste côté Spring Security : `POST /users` exige
 * `ADMIN` / `SUPER_ADMIN`, d'où le garde de route homonyme. Un `403` de
 * l'API est rendu comme un message d'accès refusé.
 */
@Component({
  selector: 'app-student-create',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './student-create.html',
  styleUrl: './student-create.scss',
})
export class StudentCreate {
  private readonly api = inject(StudentsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  /** Rappel de l'état après un échec partiel (compte ou profil déjà créé). */
  protected readonly partialNotice = signal<string | null>(null);

  protected readonly classOptions = signal<ClassOption[]>([]);
  protected readonly classesState = signal<'loading' | 'ready' | 'error'>('loading');

  /** Identifiants acquis : une reprise ne recrée pas ces objets. */
  private readonly createdUserPublicId = signal<string | null>(null);
  private readonly createdProfilePublicId = signal<string | null>(null);

  protected readonly form = this.fb.group({
    firstName: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    lastName: this.fb.control('', [Validators.required, Validators.maxLength(120)]),
    email: this.fb.control('', [Validators.required, Validators.email, Validators.maxLength(320)]),
    // Facultatif : laissé vide, le serveur génère un numéro au format
    // normalisé ESIC-AAAA-NNNNN. Le renseigner reste possible (reprise
    // d'un numéro existant), mais ce n'est plus obligatoire — une saisie
    // libre systématique cassait la norme de nommage.
    studentNumber: this.fb.control('', [Validators.maxLength(50)]),
    classGroupPublicId: this.fb.control('', [Validators.required]),
    birthDate: this.fb.control(''),
    workStudy: this.fb.control(false),
    companyName: this.fb.control('', [Validators.maxLength(191)]),
  });

  constructor() {
    this.loadClasses();
  }

  protected loadClasses(): void {
    this.classesState.set('loading');
    this.academic.listClassGroups({ status: 'ACTIVE', size: 200, sort: 'code,asc' }).subscribe({
      next: (page) => {
        this.classOptions.set(page.content.map((c) => ({ publicId: c.publicId, code: c.code })));
        this.classesState.set('ready');
      },
      error: () => this.classesState.set('error'),
    });
  }

  protected submit(): void {
    this.submitError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue();

    this.accountStep(raw)
      .pipe(
        switchMap((userPublicId) => this.profileStep(userPublicId, raw)),
        switchMap((profilePublicId) =>
          this.api
            .enrollStudent({
              studentProfilePublicId: profilePublicId,
              classGroupPublicId: raw.classGroupPublicId,
              startDate: null,
            })
            .pipe(map(() => profilePublicId)),
        ),
      )
      .subscribe({
        next: (profilePublicId) => {
          this.submitting.set(false);
          this.notifications.info(
            "Apprenant créé et inscrit. L'invitation d'activation a été envoyée.",
          );
          void this.router.navigate(['/students', profilePublicId]);
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  private accountStep(raw: ReturnType<typeof this.form.getRawValue>): Observable<string> {
    const existing = this.createdUserPublicId();
    if (existing) {
      return of(existing);
    }
    return this.api
      .createStudentAccount({
        email: raw.email.trim().toLowerCase(),
        firstName: raw.firstName.trim(),
        lastName: raw.lastName.trim(),
        role: 'STUDENT',
      })
      .pipe(
        tap((user) => this.createdUserPublicId.set(user.publicId)),
        map((user) => user.publicId),
      );
  }

  private profileStep(
    userPublicId: string,
    raw: ReturnType<typeof this.form.getRawValue>,
  ): Observable<string> {
    const existing = this.createdProfilePublicId();
    if (existing) {
      return of(existing);
    }
    return this.api
      .createStudentProfile({
        userPublicId,
        studentNumber: raw.studentNumber.trim() || null,
        birthDate: raw.birthDate || null,
        workStudy: raw.workStudy,
        companyName: raw.companyName.trim() || null,
      })
      .pipe(
        tap((profile) => this.createdProfilePublicId.set(profile.publicId)),
        map((profile) => profile.publicId),
      );
  }

  private handleError(error: unknown): void {
    this.submitting.set(false);
    const view = normalizeHttpError(error);

    if (!this.createdUserPublicId()) {
      // Étape 1 : rien n'a été créé.
      if (view.status === 409) {
        this.form.controls.email.setErrors({ server: 'Cette adresse est déjà utilisée.' });
        this.submitError.set('Cette adresse électronique correspond déjà à un compte.');
      } else if (view.status === 403) {
        this.submitError.set(
          "Vous n'êtes pas autorisé à créer un compte. Cette action est réservée à l'administration.",
        );
      } else {
        this.submitError.set(view.message);
      }
      return;
    }

    if (!this.createdProfilePublicId()) {
      // Étape 2 : le compte existe et a été invité.
      this.partialNotice.set(
        "Le compte a été créé et l'invitation envoyée, mais le profil apprenant a échoué. " +
          'Corrigez les champs concernés puis relancez : le compte ne sera pas recréé.',
      );
      if (view.status === 409 || view.code === 'ENR_STUDENT_NUMBER_TAKEN') {
        this.form.controls.studentNumber.setErrors({ server: 'Ce numéro étudiant est déjà pris.' });
      }
      this.submitError.set(view.message);
      return;
    }

    // Étape 3 : compte et profil créés, l'inscription a échoué.
    this.partialNotice.set(
      "Le compte et le profil apprenant ont été créés. L'inscription en classe a échoué : " +
        "relancez, ou inscrivez l'apprenant depuis sa fiche.",
    );
    this.submitError.set(view.message);
  }
}
