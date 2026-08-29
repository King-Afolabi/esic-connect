/**
 * Types de l'espace « Apprenants », strictement alignés sur le contrat du
 * module back-end `enrollment` (`com.esic.connect.enrollment.internal`) :
 *
 * - `GET /api/v1/student-profiles` → `PageResponse<StudentProfileResponse>`
 * - `GET /api/v1/student-profiles/{publicId}` → `StudentProfileResponse`
 * - `GET /api/v1/enrollments` → `PageResponse<EnrollmentResponse>`
 * - `GET /api/v1/enrollments/{publicId}` → `EnrollmentResponse`
 *
 * Aucun champ n'est inventé : chaque propriété correspond à un composant
 * du `record` Java associé. Ces routes sont réservées côté serveur à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`
 * (`EnrollmentWeb.MANAGE_ROLES`).
 */

/** `StudentProfileStatus` (docs/04 §11.1). */
export const STUDENT_PROFILE_STATUSES = ['ACTIVE', 'ARCHIVED'] as const;
export type StudentProfileStatus = (typeof STUDENT_PROFILE_STATUSES)[number];

/** `EnrollmentStatus` (docs/04 §13.1). */
export const ENROLLMENT_STATUSES = [
  'PENDING',
  'ACTIVE',
  'COMPLETED',
  'TRANSFERRED',
  'WITHDRAWN',
  'SUSPENDED',
  'ARCHIVED',
] as const;
export type EnrollmentStatus = (typeof ENROLLMENT_STATUSES)[number];

/** `EnrollmentSource` (docs/04 §13.1). */
export type EnrollmentSource = 'MANUAL' | 'CLASS_TRANSFER';

/** Vue API d'un profil apprenant — `StudentProfileResponse`. */
export interface StudentProfileResponse {
  publicId: string;
  userPublicId: string;
  studentNumber: string;
  /** `LocalDate` (`yyyy-MM-dd`) ou `null`. */
  birthDate: string | null;
  workStudy: boolean;
  companyName: string | null;
  status: StudentProfileStatus;
  /** `Instant` ISO-8601. */
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une inscription — `EnrollmentResponse`. */
export interface EnrollmentResponse {
  publicId: string;
  studentProfilePublicId: string;
  studentNumber: string;
  classGroupPublicId: string;
  classGroupCode: string;
  programPublicId: string;
  programCode: string;
  academicYearPublicId: string;
  academicYearCode: string;
  startDate: string;
  endDate: string | null;
  status: EnrollmentStatus;
  enrollmentSource: EnrollmentSource;
  changeReason: string | null;
  previousEnrollmentPublicId: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Enveloppe de pagination commune (`PageResponse<T>` côté serveur). Le
 * format JSON par défaut de `Page` est explicitement déconseillé : le
 * back-end renvoie cette forme stable.
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Sous-ensemble de `UserDetailResponse`
 * (`GET /api/v1/users/{publicId}`, même périmètre de rôles). Consommé de
 * façon **facultative** par la fiche apprenant pour afficher l'identité
 * civile : le profil apprenant n'expose que `userPublicId`. Un échec de
 * cet appel n'empêche jamais l'affichage du profil.
 */
export interface UserIdentitySummary {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
}

/**
 * Champs de tri réellement acceptés par `GET /api/v1/student-profiles`
 * (liste blanche `StudentProfileService.SORTABLE` ; toute autre valeur →
 * 400 `ENR_INVALID_SORT`).
 */
export const STUDENT_PROFILE_SORT_FIELDS = ['studentNumber', 'createdAt'] as const;
export type StudentProfileSortField = (typeof STUDENT_PROFILE_SORT_FIELDS)[number];

export type SortDirection = 'asc' | 'desc';

/** Paramètres de `GET /api/v1/student-profiles` réellement exposés. */
export interface StudentProfileListQuery {
  /** `q` — sous-chaîne du **numéro étudiant** uniquement (pas le nom). */
  q?: string | null;
  status?: StudentProfileStatus | null;
  /** `sort` — `champ` ou `champ,asc|desc`, champ dans la liste blanche. */
  sort?: string | null;
  page?: number;
  size?: number;
}

/** Paramètres de `GET /api/v1/enrollments` réellement exposés. */
export interface EnrollmentListQuery {
  /** `student` — `public_id` d'un profil apprenant. */
  student?: string | null;
  /** `classGroup` — `public_id` d'une classe. */
  classGroup?: string | null;
  status?: EnrollmentStatus | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

export const STUDENT_PROFILE_STATUS_LABELS: Record<StudentProfileStatus, string> = {
  ACTIVE: 'Actif',
  ARCHIVED: 'Archivé',
};

export const ENROLLMENT_STATUS_LABELS: Record<EnrollmentStatus, string> = {
  PENDING: 'En attente',
  ACTIVE: 'Active',
  COMPLETED: 'Terminée',
  TRANSFERRED: 'Changement de classe',
  WITHDRAWN: 'Abandon',
  SUSPENDED: 'Suspendue',
  ARCHIVED: 'Archivée',
};

export const ENROLLMENT_SOURCE_LABELS: Record<EnrollmentSource, string> = {
  MANUAL: 'Saisie manuelle',
  CLASS_TRANSFER: "Issue d'un changement de classe",
};

export function studentProfileStatusLabel(status: string): string {
  return (STUDENT_PROFILE_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

export function enrollmentStatusLabel(status: string): string {
  return (ENROLLMENT_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

export function enrollmentSourceLabel(source: string): string {
  return (ENROLLMENT_SOURCE_LABELS as Record<string, string>)[source] ?? source;
}
