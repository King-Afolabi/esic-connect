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
  /** Identité civile du compte lié — `null` si non résolue. */
  firstName: string | null;
  lastName: string | null;
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

// -------------------------------------------------------------------
// Création manuelle d'un apprenant (Lot H) — enchaîne trois routes
// existantes, chacune contrôlée côté serveur :
//   1. POST /api/v1/users            (ADMIN / SUPER_ADMIN)  — compte + invitation
//   2. POST /api/v1/student-profiles (EnrollmentWeb.MANAGE_ROLES) — profil
//   3. POST /api/v1/enrollments      (EnrollmentWeb.MANAGE_ROLES) — inscription
// Aucun champ inventé : ils reprennent CreateUserRequest,
// StudentProfileRequests.Create et EnrollmentRequests.Enroll.
// -------------------------------------------------------------------

/** Corps de `POST /api/v1/users` — `role` fixé à `STUDENT` par l'écran. */
export interface CreateStudentAccountRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: 'STUDENT';
}

/** Réponse `UserDetailResponse` — seul `publicId` est consommé ici. */
export interface CreatedUserResponse {
  publicId: string;
}

/** Corps de `POST /api/v1/student-profiles`. */
export interface CreateStudentProfileRequest {
  userPublicId: string;
  /** Vide / `null` : le serveur génère `ESIC-AAAA-NNNNN`. */
  studentNumber: string | null;
  birthDate?: string | null;
  workStudy?: boolean;
  companyName?: string | null;
}

/** Corps de `POST /api/v1/enrollments` (`EnrollmentRequests.Enroll`). */
export interface EnrollStudentRequest {
  studentProfilePublicId: string;
  classGroupPublicId: string;
  startDate?: string | null;
}

/**
 * Corps de `POST /api/v1/enrollments/{publicId}/transfer`
 * (`EnrollmentRequests.Transfer`) — changement de classe.
 */
export interface TransferEnrollmentRequest {
  classGroupPublicId: string;
  reason: string;
  effectiveDate?: string | null;
}

/**
 * Corps de `POST /api/v1/enrollments/{publicId}/close`
 * (`EnrollmentRequests.Close`).
 */
export interface CloseEnrollmentRequest {
  status: 'COMPLETED' | 'WITHDRAWN';
  reason: string;
  effectiveDate?: string | null;
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

// ---------------------------------------------------------------------
// Suivi à distance individuel (EF-ENR-004 ; docs/02 §15.3)
// ---------------------------------------------------------------------

export type RemoteAuthorizationStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

export const REMOTE_AUTHORIZATION_STATUS_LABELS: Record<RemoteAuthorizationStatus, string> = {
  ACTIVE: 'Active',
  REVOKED: 'Révoquée',
  EXPIRED: 'Expirée',
};

export function remoteAuthorizationStatusLabel(value: string): string {
  return (REMOTE_AUTHORIZATION_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/**
 * `enrollment.internal.RemoteAttendanceResponse`.
 *
 * `classGroupPublicId` nul = autorisation **générale** (toutes les classes
 * de l'apprenant). Une autorisation révoquée n'est jamais supprimée : la
 * décision reste au dossier.
 */
export interface RemoteAttendanceAuthorizationResponse {
  publicId: string;
  studentUserPublicId: string;
  classGroupPublicId: string | null;
  status: RemoteAuthorizationStatus;
  reason: string;
  validFrom: string;
  validUntil: string | null;
  decidedAt: string;
  revokedAt: string | null;
  revocationReason: string | null;
  createdAt: string;
}

/** Corps de `POST /api/v1/remote-attendance-authorizations`. */
export interface RemoteAttendanceAuthorizeRequest {
  studentUserPublicId: string;
  classGroupPublicId?: string | null;
  reason: string;
  validFrom: string;
  validUntil?: string | null;
}
