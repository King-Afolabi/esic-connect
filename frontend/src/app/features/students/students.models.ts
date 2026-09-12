/**
 * Types de l'espace « Apprenants », strictement alignés sur le contrat du
 * module back-end `enrollment` (`com.esic.connect.enrollment.internal`) :
 *
 * - `GET /api/v1/students` → `PageResponse<StudentResponse>`
 * - `GET /api/v1/students/{userPublicId}` → `StudentResponse`
 * - `GET /api/v1/enrollments` → `PageResponse<EnrollmentResponse>`
 * - `GET /api/v1/enrollments/{publicId}` → `EnrollmentResponse`
 * - `POST /api/v1/student-profiles` → `StudentProfileResponse`
 *
 * Refonte 2026-09 : le rôle `STUDENT` (module `identity`) est l'unique
 * source de vérité du statut apprenant. `GET /api/v1/students` liste
 * **tous** les comptes porteurs de ce rôle — avec ou sans
 * `student_profile`, avec ou sans `enrollment` — et ne les liste jamais
 * deux fois, même en cas d'inscriptions multiples. `student_profile` et
 * `enrollment` restent des données facultatives, exposées comme
 * décorations optionnelles de `StudentResponse` (champs nullables).
 *
 * Aucun champ n'est inventé : chaque propriété correspond à un composant
 * du `record` Java associé. Ces routes sont réservées côté serveur à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` (accès global) et
 * `PEDAGOGICAL_MANAGER` / `TEACHER` (périmètre restreint côté serveur,
 * `EnrollmentWeb.READ_ROLES`).
 */

/** Statut de compte (`AccountStatus`, module identity). */
export const STUDENT_ACCOUNT_STATUSES = [
  'PENDING_ACTIVATION',
  'ACTIVE',
  'SUSPENDED',
  'LOCKED',
  'ARCHIVED',
] as const;
export type StudentAccountStatus = (typeof STUDENT_ACCOUNT_STATUSES)[number];

/** `StudentProfileStatus` (docs/04 §11.1) — statut du profil facultatif. */
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

/**
 * Vue API d'un apprenant — `StudentResponse`. Un compte porteur du rôle
 * `STUDENT`, décoré (jamais conditionné) par son profil facultatif et son
 * inscription courante (`ACTIVE`, sinon la plus récente).
 *
 * Les champs `studentProfilePublicId` à `profileStatus` sont `null`
 * lorsque le compte n'a pas de `student_profile` ; les champs
 * `currentEnrollmentPublicId` à `enrollmentStatus` sont `null` lorsque le
 * compte n'a aucune inscription. Aucun des deux groupes n'est requis pour
 * qu'un apprenant figure dans la liste.
 */
export interface StudentResponse {
  userPublicId: string;
  email: string;
  firstName: string;
  lastName: string;
  accountStatus: StudentAccountStatus;
  /** `Instant` ISO-8601. */
  createdAt: string;
  lastLoginAt: string | null;

  // Décoration facultative : student_profile.
  studentProfilePublicId: string | null;
  studentNumber: string | null;
  /** `LocalDate` (`yyyy-MM-dd`) ou `null`. */
  birthDate: string | null;
  workStudy: boolean | null;
  companyName: string | null;
  profileStatus: StudentProfileStatus | null;

  // Décoration facultative : inscription courante.
  currentEnrollmentPublicId: string | null;
  classGroupPublicId: string | null;
  classGroupCode: string | null;
  academicYearPublicId: string | null;
  academicYearCode: string | null;
  enrollmentStatus: EnrollmentStatus | null;
}

/**
 * Vue API d'un profil apprenant — `StudentProfileResponse`
 * (`POST /api/v1/student-profiles`, ajout de données facultatives à un
 * compte `STUDENT` existant).
 */
export interface StudentProfileResponse {
  publicId: string;
  userPublicId: string;
  /** Identité civile du compte lié — `null` si non résolue. */
  firstName: string | null;
  lastName: string | null;
  studentNumber: string;
  birthDate: string | null;
  workStudy: boolean;
  companyName: string | null;
  status: StudentProfileStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * Vue API d'une inscription — `EnrollmentResponse`. Rattache directement
 * un **compte** (`studentUserPublicId`, toujours renseigné) ; le profil
 * apprenant (`studentProfilePublicId` / `studentNumber`) reste une
 * décoration facultative, `null` si le compte n'a pas de profil.
 */
export interface EnrollmentResponse {
  publicId: string;
  studentUserPublicId: string;
  studentProfilePublicId: string | null;
  studentNumber: string | null;
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

// -------------------------------------------------------------------
// Création manuelle d'un apprenant (Lot H) — enchaîne trois routes
// existantes, chacune contrôlée côté serveur :
//   1. POST /api/v1/users            (ADMIN / SUPER_ADMIN)  — compte + invitation
//   2. POST /api/v1/student-profiles (EnrollmentWeb.MANAGE_ROLES) — profil (facultatif)
//   3. POST /api/v1/enrollments      (EnrollmentWeb.MANAGE_ROLES) — inscription (facultative)
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

/**
 * Corps de `POST /api/v1/enrollments` (`EnrollmentRequests.Enroll`) —
 * vise directement le **compte** apprenant, jamais un profil (refonte
 * 2026-09) : l'inscription ne suppose l'existence d'aucun profil.
 */
export interface EnrollStudentRequest {
  studentUserPublicId: string;
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
 * Champs de tri réellement acceptés par `GET /api/v1/students` (liste
 * blanche `StudentDirectoryService.SORTABLE` ; toute autre valeur → 400
 * `ENR_INVALID_SORT`).
 */
export const STUDENT_SORT_FIELDS = ['lastName', 'email', 'createdAt', 'lastLoginAt'] as const;
export type StudentSortField = (typeof STUDENT_SORT_FIELDS)[number];

export type SortDirection = 'asc' | 'desc';

/** Paramètres de `GET /api/v1/students` réellement exposés. */
export interface StudentListQuery {
  /** `q` — sous-chaîne du nom, prénom ou e-mail (jamais un critère d'énumération isolé). */
  q?: string | null;
  /** Statut du **compte** (`AccountStatus`) — pas le statut du profil. */
  status?: StudentAccountStatus | null;
  /** `sort` — `champ` ou `champ,asc|desc`, champ dans la liste blanche. */
  sort?: string | null;
  page?: number;
  size?: number;
}

/** Paramètres de `GET /api/v1/enrollments` réellement exposés. */
export interface EnrollmentListQuery {
  /** `student` — `public_id` du **compte** apprenant (refonte 2026-09). */
  student?: string | null;
  /** `classGroup` — `public_id` d'une classe. */
  classGroup?: string | null;
  status?: EnrollmentStatus | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

export const STUDENT_ACCOUNT_STATUS_LABELS: Record<StudentAccountStatus, string> = {
  PENDING_ACTIVATION: "En attente d'activation",
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  LOCKED: 'Verrouillé',
  ARCHIVED: 'Archivé',
};

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

export function studentAccountStatusLabel(status: string): string {
  return (STUDENT_ACCOUNT_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

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
