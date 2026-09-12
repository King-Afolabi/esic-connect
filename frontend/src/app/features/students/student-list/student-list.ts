import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { ListQueryReader, writeListQueryParams } from '../../../core/navigation/list-query-params';
import { normalizeHttpError } from '../../../core/models/api-error';
import { NotificationService } from '../../../core/notifications/notification.service';
import { StudentsApiService } from '../students-api.service';
import {
  PageResponse,
  STUDENT_ACCOUNT_STATUSES,
  STUDENT_SORT_FIELDS,
  SortDirection,
  StudentAccountStatus,
  StudentResponse,
  StudentSortField,
  studentAccountStatusLabel,
} from '../students.models';

/** État de la consultation de la liste. */
type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<StudentResponse> };

const DEFAULT_SORT_FIELD: StudentSortField = 'createdAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des apprenants — `GET /api/v1/students`.
 *
 * Refonte 2026-09 : le rôle {@code STUDENT} (module {@code identity}) est
 * l'unique source de vérité du statut apprenant. Cette liste montre donc
 * **tous** les comptes porteurs de ce rôle — avec ou sans profil
 * apprenant, avec ou sans inscription — sans qu'aucun panneau séparé ni
 * comparaison côté client ne soit nécessaire pour les « retrouver » : il
 * n'existe plus de compte {@code STUDENT} invisible ici.
 *
 * Recherche, filtre, tri et pagination reflètent exactement ce que l'API
 * accepte : recherche `q` sur le nom, le prénom ou l'e-mail, filtre
 * `status` sur le **statut du compte**, tri sur `lastName` / `email` /
 * `createdAt` / `lastLoginAt`, pagination bornée à 100.
 *
 * Le contrôle d'accès reste côté Spring Security : un `403` renvoyé par
 * l'API est rendu comme un état « accès refusé » explicite, même si le
 * `roleGuard` de la route a normalement déjà filtré l'utilisateur.
 */
@Component({
  selector: 'app-student-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './student-list.html',
  styleUrl: './student-list.scss',
})
export class StudentList {
  private readonly api = inject(StudentsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /**
   * « Ajouter un apprenant » (Lot H) : visible uniquement pour les rôles
   * qui peuvent réellement créer le compte côté serveur (`POST /users`
   * exige `ADMIN` / `SUPER_ADMIN`). Le garde de route reste l'autorité.
   */
  protected readonly canCreateStudent = computed(() =>
    this.roleContext.effectiveRoles().some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN'),
  );

  /**
   * « Importer des apprenants » : l'import CSV n'a pas d'entrée racine
   * dans la navigation (ANO-NAV-001) — il est atteint d'ici. Périmètre
   * aligné sur `StudentImportWeb.MANAGE_ROLES` (les quatre rôles qui
   * peuvent importer, `PEDAGOGICAL_MANAGER` compris — limité à son
   * périmètre côté serveur) ; le garde de route `/students/import` reste
   * l'autorité. Un `TEACHER` consulte la liste mais n'importe pas.
   */
  protected readonly canImportStudents = computed(() =>
    this.roleContext
      .effectiveRoles()
      .some(
        (r) =>
          r === 'ADMIN' ||
          r === 'SUPER_ADMIN' ||
          r === 'SCHOOL_ADMINISTRATION' ||
          r === 'PEDAGOGICAL_MANAGER',
      ),
  );

  /** Écriture sur les inscriptions (attribuer une classe) — `EnrollmentWeb.MANAGE_ROLES`. */
  protected readonly canManageEnrollment = computed(() =>
    this.roleContext
      .effectiveRoles()
      .some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN' || r === 'SCHOOL_ADMINISTRATION'),
  );

  protected readonly statuses = STUDENT_ACCOUNT_STATUSES;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly statusLabel = studentAccountStatusLabel;
  protected readonly displayedColumns = [
    'name',
    'email',
    'studentNumber',
    'classGroup',
    'workStudy',
    'companyName',
    'status',
    'createdAt',
    'actions',
  ] as const;

  /** Filtres appliqués (nom / prénom / e-mail + statut du compte). */
  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<StudentAccountStatus | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });

  protected readonly sortField = signal<StudentSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly classGroups = signal<ClassGroupResponse[]>([]);

  /** Ligne (compte) pour laquelle le formulaire « attribuer une classe » est ouvert. */
  protected readonly assigningUserId = signal<string | null>(null);
  protected readonly assignForm = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control('', [Validators.required]),
  });
  protected readonly assignSubmitting = signal(false);
  protected readonly assignError = signal<string | null>(null);

  protected readonly rows = computed<StudentResponse[]>(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.content : [];
  });
  protected readonly totalElements = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.totalElements : 0;
  });
  protected readonly isEmpty = computed(() => {
    const current = this.state();
    return current.kind === 'ready' && current.page.content.length === 0;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    // Lot G : restaure filtres / tri / pagination depuis l'URL — une
    // fiche ouverte puis « Retour » (ou une URL partagée) retrouve l'état.
    const params = new ListQueryReader(this.route);
    this.filters.patchValue({
      q: params.str('q'),
      status: params.oneOf('status', [...STUDENT_ACCOUNT_STATUSES, ''] as const, ''),
    });
    this.sortField.set(params.oneOf('sort', STUDENT_SORT_FIELDS, DEFAULT_SORT_FIELD));
    this.sortDirection.set(params.direction('dir', DEFAULT_SORT_DIRECTION));
    this.pageIndex.set(params.int('page', 0));
    this.pageSize.set(params.int('size', 20));
    this.load();
    this.loadClassGroups();
  }

  protected applyFilters(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset({ q: '', status: '' });
    this.pageIndex.set(0);
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = STUDENT_SORT_FIELDS.includes(sort.active as StudentSortField)
      ? (sort.active as StudentSortField)
      : DEFAULT_SORT_FIELD;
    this.sortField.set(field);
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
    this.load();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected hasNoClass(row: StudentResponse): boolean {
    return row.classGroupPublicId === null;
  }

  // -------------------------------------------------------------------
  // Attribuer une classe à un apprenant sans inscription active
  // -------------------------------------------------------------------

  protected startAssign(userPublicId: string): void {
    this.assigningUserId.set(userPublicId);
    this.assignError.set(null);
    this.assignForm.reset({ classGroupPublicId: '' });
  }

  protected cancelAssign(): void {
    this.assigningUserId.set(null);
    this.assignError.set(null);
  }

  protected confirmAssign(): void {
    const userPublicId = this.assigningUserId();
    if (!userPublicId || this.assignForm.invalid || this.assignSubmitting()) {
      this.assignForm.markAllAsTouched();
      return;
    }
    this.assignSubmitting.set(true);
    this.assignError.set(null);
    this.api
      .enrollStudent({
        studentUserPublicId: userPublicId,
        classGroupPublicId: this.assignForm.getRawValue().classGroupPublicId,
      })
      .subscribe({
        next: () => {
          this.assignSubmitting.set(false);
          this.assigningUserId.set(null);
          this.notifications.info('Classe attribuée.');
          this.load();
        },
        error: (error: unknown) => {
          this.assignSubmitting.set(false);
          this.assignError.set(normalizeHttpError(error).message);
        },
      });
  }

  private loadClassGroups(): void {
    this.academic.listClassGroups({ status: 'ACTIVE', size: 200, sort: 'code,asc' }).subscribe({
      next: (page) => this.classGroups.set(page.content),
      error: () => this.classGroups.set([]),
    });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.syncUrl(raw.q.trim(), raw.status);
    this.api
      .listStudents({
        q: raw.q.trim() || null,
        status: raw.status || null,
        sort: `${this.sortField()},${this.sortDirection()}`,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', page }),
        error: (error: unknown) => {
          const normalized = normalizeHttpError(error);
          if (normalized.status === 403) {
            this.state.set({ kind: 'forbidden' });
            return;
          }
          this.state.set({ kind: 'error', message: normalized.message });
        },
      });
  }

  /** Lot G : reflète l'état courant dans l'URL (défauts non écrits). */
  private syncUrl(q: string, status: string): void {
    writeListQueryParams(this.router, this.route, {
      q,
      status,
      sort: this.sortField() === DEFAULT_SORT_FIELD ? null : this.sortField(),
      dir: this.sortDirection() === DEFAULT_SORT_DIRECTION ? null : this.sortDirection(),
      page: this.pageIndex(),
      size: this.pageSize() === 20 ? null : this.pageSize(),
    });
  }
}

/** Libellés français du paginateur Material (docs/02 §38.7). */
export function frenchPaginatorIntl(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Éléments par page';
  intl.nextPageLabel = 'Page suivante';
  intl.previousPageLabel = 'Page précédente';
  intl.firstPageLabel = 'Première page';
  intl.lastPageLabel = 'Dernière page';
  intl.getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 sur ${length}`;
    }
    const start = page * pageSize + 1;
    const end = Math.min(start + pageSize - 1, length);
    return `${start} – ${end} sur ${length}`;
  };
  return intl;
}
