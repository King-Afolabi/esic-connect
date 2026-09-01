/**
 * Types du parcours **Séances et émargement**, strictement alignés sur le
 * contrat des modules back-end `coursesession`
 * (`com.esic.connect.coursesession.internal`) et `attendance`
 * (`com.esic.connect.attendance.internal`) — aucun champ, endpoint,
 * statut HTTP, rôle ni code `SESSION_*` / `ATT_*` n'est inventé.
 *
 * Séances (`/api/v1/sessions`) :
 * - `GET  /sessions`                       → `PageResponse<CourseSessionResponse>`
 * - `GET  /sessions/teachers`              → `TeacherOptionResponse[]`
 * - `GET  /sessions/{publicId}`            → `CourseSessionResponse`
 * - `POST /sessions`                       → `CourseSessionResponse` (201)
 * - `POST /sessions/{publicId}/open`       → 204
 * - `POST /sessions/{publicId}/close`      → 204
 *
 * Émargement :
 * - `POST /sessions/{publicId}/attendance-token` → `AttendanceTokenResponse`
 * - `GET  /sessions/{publicId}/attendance`       → `SessionAttendanceResponse`
 * - `POST /attendance/validate`                  → `AttendanceRecordResponse` (200)
 *
 * Rôles : lecture des séances =
 * `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`/`TEACHER`
 * (un `TEACHER` ne voit que ses séances, un `PEDAGOGICAL_MANAGER` que son
 * périmètre, décidé **côté serveur**) ; création =
 * `ADMIN`/`SUPER_ADMIN`/`PEDAGOGICAL_MANAGER` ; ouverture / fermeture /
 * jeton = ces rôles + `TEACHER` (pas `SCHOOL_ADMINISTRATION`) ;
 * `POST /attendance/validate` = `STUDENT` uniquement.
 */

/** Enveloppe de pagination stable (`PageResponse<T>` côté serveur). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type SortDirection = 'asc' | 'desc';

// ---------------------------------------------------------------------------
// Énumérations (valeurs exactes du back-end)
// ---------------------------------------------------------------------------

/** `SessionLifecycle` — `CANCELLED` ajouté au bloc G1-C. */
export const SESSION_STATUSES = ['PLANNED', 'OPEN', 'CLOSED', 'CANCELLED'] as const;
export type SessionStatus = (typeof SESSION_STATUSES)[number];

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  PLANNED: 'Planifiée',
  OPEN: 'Ouverte',
  CLOSED: 'Clôturée',
  CANCELLED: 'Annulée',
};

export function sessionStatusLabel(status: string): string {
  return (SESSION_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `AttendanceRecordSource` (V10 : + MANUAL, CORRECTION). */
export type AttendanceSource = 'DYNAMIC_QR' | 'SHORT_CODE' | 'MANUAL' | 'CORRECTION';

export const ATTENDANCE_SOURCE_LABELS: Record<AttendanceSource, string> = {
  DYNAMIC_QR: 'QR dynamique',
  SHORT_CODE: 'Code court',
  MANUAL: 'Saisie manuelle',
  CORRECTION: 'Correction',
};

export function attendanceSourceLabel(source: string): string {
  return (ATTENDANCE_SOURCE_LABELS as Record<string, string>)[source] ?? source;
}

/** `AttendanceCheckpointType` (V10). */
export const CHECKPOINT_TYPES = ['START', 'END', 'CUSTOM'] as const;
export type CheckpointType = (typeof CHECKPOINT_TYPES)[number];
export const CHECKPOINT_TYPE_LABELS: Record<CheckpointType, string> = {
  START: 'Arrivée',
  END: 'Fin',
  CUSTOM: 'Intermédiaire',
};
export function checkpointTypeLabel(value: string): string {
  return (CHECKPOINT_TYPE_LABELS as Record<string, string>)[value] ?? value;
}

/** `AttendanceCheckpointStatus` (V10). */
export const CHECKPOINT_STATUSES = ['PLANNED', 'OPEN', 'CLOSED', 'CANCELLED'] as const;
export type CheckpointStatus = (typeof CHECKPOINT_STATUSES)[number];
export const CHECKPOINT_STATUS_LABELS: Record<CheckpointStatus, string> = {
  PLANNED: 'Planifié',
  OPEN: 'Ouvert',
  CLOSED: 'Fermé',
  CANCELLED: 'Annulé',
};
export function checkpointStatusLabel(value: string): string {
  return (CHECKPOINT_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** `AttendanceStatus` (V10). */
export const ATTENDANCE_STATUSES = [
  'PRESENT',
  'LATE',
  'ABSENT',
  'EXCUSED_ABSENCE',
  'CANCELLED',
] as const;
export type AttendanceStatus = (typeof ATTENDANCE_STATUSES)[number];
export const ATTENDANCE_STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Présent',
  LATE: 'En retard',
  ABSENT: 'Absent',
  EXCUSED_ABSENCE: 'Absence excusée',
  CANCELLED: 'Annulée',
};
export function attendanceStatusLabel(value: string): string {
  return (ATTENDANCE_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** `CourseSessionResponse.CheckpointView` / `CheckpointResponse`. */
export interface CheckpointView {
  publicId: string;
  label: string;
  type: CheckpointType;
  status: CheckpointStatus;
  required: boolean;
  displayOrder: number;
  openedAt: string | null;
  closedAt: string | null;
  cancelReason?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** `CheckpointRequests.Create`. */
export interface CreateCheckpointRequest {
  label: string;
  type: CheckpointType;
  required?: boolean;
  displayOrder?: number;
}

/** `AttendanceManagementRequests.ManualRecord`. */
export interface ManualAttendanceRequest {
  enrollmentPublicId: string;
  checkpointPublicId: string;
  status: 'PRESENT' | 'LATE' | 'ABSENT';
  lateMinutes?: number | null;
  comment: string;
}

/** `AttendanceManagementRequests.Correct`. */
export interface CorrectAttendanceRequest {
  status?: 'PRESENT' | 'LATE' | 'ABSENT' | null;
  lateMinutes?: number | null;
  comment?: string | null;
  reason: string;
}

/** `AttendanceManagementRequests.Cancel`. */
export interface CancelAttendanceRequest {
  reason: string;
}

/**
 * `AttendanceCandidateResponse` — candidat à une saisie manuelle de
 * présence : inscription active d'une classe de la séance. Le back-end
 * n'expose ni e-mail ni identifiant SQL ; le contrôle fin est celui de la
 * lecture des présences.
 */
export interface AttendanceCandidate {
  studentProfilePublicId: string | null;
  enrollmentPublicId: string;
  studentNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  classCode: string | null;
}

/** Libellé lisible d'un candidat (nom + numéro), sans e-mail. */
export function attendanceCandidateLabel(candidate: AttendanceCandidate): string {
  const name = [candidate.firstName, candidate.lastName].filter((part) => !!part).join(' ').trim();
  const number = candidate.studentNumber ? ` — ${candidate.studentNumber}` : '';
  const clazz = candidate.classCode ? ` (${candidate.classCode})` : '';
  return `${name || 'Apprenant'}${number}${clazz}`;
}

/** `AttendanceCorrectionResponse` — une entrée d'historique append-only. */
export interface AttendanceCorrectionEntry {
  publicId: string;
  action: string;
  previousStatus: AttendanceStatus | null;
  newStatus: AttendanceStatus | null;
  previousLateMinutes: number | null;
  newLateMinutes: number | null;
  previousComment: string | null;
  newComment: string | null;
  reason: string;
  occurredAt: string;
}

export const CORRECTION_ACTION_LABELS: Record<string, string> = {
  CREATED_MANUALLY: 'Créée manuellement',
  STATUS_CORRECTED: 'Statut corrigé',
  CANCELLED: 'Annulée',
  JUSTIFICATION_ADDED: 'Justificatif déposé',
  JUSTIFICATION_UPDATED: 'Justificatif modifié',
  JUSTIFICATION_REVIEWED: 'Justificatif examiné',
};
export function correctionActionLabel(value: string): string {
  return CORRECTION_ACTION_LABELS[value] ?? value;
}

// ---------------------------------------------------------------------------
// Réponses API
// ---------------------------------------------------------------------------

/** `CourseSessionResponse.TeacherView`. */
export interface SessionTeacherView {
  publicId: string | null;
  firstName: string | null;
  lastName: string | null;
}

/** `CourseSessionResponse.SessionClassView`. */
export interface SessionClassView {
  publicId: string;
  code: string;
}

/** `CourseSessionResponse` — jamais d'identifiant SQL, jamais de jeton. */
export interface CourseSessionResponse {
  publicId: string;
  status: SessionStatus;
  title: string | null;
  exceptionReason: string;
  teacher: SessionTeacherView;
  classes: SessionClassView[];
  /** `Instant` ISO-8601. */
  startsAt: string;
  endsAt: string;
  timeZoneId: string;
  openedAt: string | null;
  closedAt: string | null;
  /** G1-C : motif + instant d'annulation (non nuls uniquement si `status === 'CANCELLED'`). */
  cancellationReason: string | null;
  cancelledAt: string | null;
  /** Compat V9 : premier point de contrôle (START). */
  checkpointPublicId: string | null;
  checkpointOpen: boolean;
  /** V10 : liste complète des points de contrôle, triée par ordre d'affichage. */
  checkpoints: CheckpointView[];
  createdAt: string;
  updatedAt: string;
}

/** `TeacherOptionResponse`. */
export interface TeacherOptionResponse {
  publicId: string;
  firstName: string;
  lastName: string;
}

/** `AttendanceTokenResponse` — `token` / `shortCode` en mémoire uniquement. */
export interface AttendanceTokenResponse {
  token: string;
  shortCode: string;
  expiresAt: string;
  sessionPublicId: string;
  /** V10 : point de contrôle ciblé par ce jeton. */
  checkpointPublicId: string;
  ttlSeconds: number;
}

/** `AttendanceRecordResponse` — récépissé d'un émargement / d'une saisie. */
export interface AttendanceRecordResponse {
  attendancePublicId: string;
  sessionPublicId: string;
  checkpointPublicId: string | null;
  sessionTitle: string | null;
  status: AttendanceStatus;
  lateMinutes: number | null;
  recordedAt: string;
  source: AttendanceSource;
}

/** `SessionAttendanceResponse.Row`. */
export interface SessionAttendanceRow {
  attendancePublicId: string | null;
  studentProfilePublicId: string | null;
  enrollmentPublicId: string | null;
  studentNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  status: AttendanceStatus;
  lateMinutes: number | null;
  comment: string | null;
  recordedAt: string;
  source: AttendanceSource;
}

/** `SessionAttendanceResponse.CheckpointAttendance`. */
export interface CheckpointAttendance {
  checkpointPublicId: string;
  label: string;
  type: CheckpointType;
  status: CheckpointStatus;
  required: boolean;
  expectedCount: number;
  presentCount: number;
  lateCount: number;
  absentCount: number;
  excusedCount: number;
  derivedAbsentCount: number;
  records: SessionAttendanceRow[];
}

/** `SessionAttendanceResponse` (V10 — compat top-level = premier point de contrôle). */
export interface SessionAttendanceResponse {
  sessionPublicId: string;
  checkpointPublicId: string | null;
  expectedCount: number;
  presentCount: number;
  records: SessionAttendanceRow[];
  checkpoints: CheckpointAttendance[];
}

// ---------------------------------------------------------------------------
// Requêtes (corps POST) — noms exacts des `record` back-end
// ---------------------------------------------------------------------------

/** `CourseSessionRequests.Create`. */
export interface CreateSessionRequest {
  teacherPublicId: string;
  classPublicIds: string[];
  /** `Instant` ISO-8601. */
  startsAt: string;
  endsAt: string;
  timeZoneId: string;
  reason: string;
  title?: string | null;
}

/** `CourseSessionRequests.Cancel` (G1-C) — motif obligatoire, borné à 500. */
export interface CancelSessionRequest {
  reason: string;
}

/** `AttendanceRequests.Validate` — exactement l'un des deux champs. */
export interface ValidateAttendanceRequest {
  token?: string | null;
  shortCode?: string | null;
}

// ---------------------------------------------------------------------------
// Paramètres de liste (uniquement ceux réellement exposés)
// ---------------------------------------------------------------------------

/** `CourseSessionService.SORTABLE`. */
export const SESSION_SORT_FIELDS = ['startsAt', 'createdAt'] as const;
export type SessionSortField = (typeof SESSION_SORT_FIELDS)[number];

/** Rôles autorisés à créer une séance (`CourseSessionWeb.CREATE_ROLES`). */
export const SESSION_CREATE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'] as const;

/** Rôles autorisés à lire la fiche d'une séance (`CourseSessionWeb.READ_ROLES`). */
export const SESSION_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
] as const;

/**
 * Rôles autorisés à gérer les points de contrôle, ouvrir / fermer la
 * séance et émettre un jeton QR (`CourseSessionWeb.MANAGE_ROLES`).
 * `SCHOOL_ADMINISTRATION` en est exclu (lecture seule des séances).
 */
export const SESSION_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
] as const;

/**
 * Rôles autorisés à la saisie manuelle des présences, à la correction et
 * à l'annulation, et à charger les candidats
 * (`AttendanceManagementWeb.MANAGE_ROLES`). Distinct de
 * {@link SESSION_MANAGE_ROLES} : `SCHOOL_ADMINISTRATION` peut agir sur
 * les présences mais **pas** sur les points de contrôle ni le QR.
 */
export const SESSION_ATTENDANCE_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
] as const;

/** Vrai si `roles` contient au moins un des rôles requis. */
export function holdsAnySessionRole(roles: readonly string[], required: readonly string[]): boolean {
  return roles.some((role) => required.includes(role));
}

export interface SessionListQuery {
  status?: SessionStatus | null;
  /** `teacher` — `public_id` d'un compte formateur. */
  teacher?: string | null;
  /** `classGroup` — `public_id` d'une classe. */
  classGroup?: string | null;
  /** `from` / `to` — `Instant` ISO-8601 sur `startsAt`. */
  from?: string | null;
  to?: string | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Formatage (déterministe, UTC — comme le reste du front)
// ---------------------------------------------------------------------------

/** `Instant` ISO-8601 → `jj/mm/aaaa hh:mm UTC`. `—` si absent / illisible. */
export function formatInstantUtc(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const day = String(date.getUTCDate()).padStart(2, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  return `${day}/${month}/${date.getUTCFullYear()} ${hh}:${mm} UTC`;
}

/** Concatène les codes de classe d'une séance pour un affichage compact. */
export function classCodes(session: Pick<CourseSessionResponse, 'classes'>): string {
  return session.classes.map((c) => c.code).join(', ') || '—';
}

/** Nom affichable d'un formateur (`— ` si non résolu). */
export function teacherName(teacher: SessionTeacherView | TeacherOptionResponse): string {
  const first = 'firstName' in teacher ? teacher.firstName : null;
  const last = 'lastName' in teacher ? teacher.lastName : null;
  const full = [first, last].filter(Boolean).join(' ').trim();
  return full || '—';
}
