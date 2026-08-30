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

/** `SessionLifecycle`. */
export const SESSION_STATUSES = ['PLANNED', 'OPEN', 'CLOSED'] as const;
export type SessionStatus = (typeof SESSION_STATUSES)[number];

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  PLANNED: 'Planifiée',
  OPEN: 'Ouverte',
  CLOSED: 'Clôturée',
};

export function sessionStatusLabel(status: string): string {
  return (SESSION_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `AttendanceRecordSource`. */
export type AttendanceSource = 'DYNAMIC_QR' | 'SHORT_CODE';

export const ATTENDANCE_SOURCE_LABELS: Record<AttendanceSource, string> = {
  DYNAMIC_QR: 'QR dynamique',
  SHORT_CODE: 'Code court',
};

export function attendanceSourceLabel(source: string): string {
  return (ATTENDANCE_SOURCE_LABELS as Record<string, string>)[source] ?? source;
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
  checkpointPublicId: string | null;
  checkpointOpen: boolean;
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
  ttlSeconds: number;
}

/** `AttendanceRecordResponse` — récépissé d'un émargement. */
export interface AttendanceRecordResponse {
  attendancePublicId: string;
  sessionPublicId: string;
  sessionTitle: string | null;
  recordedAt: string;
  source: AttendanceSource;
}

/** `SessionAttendanceResponse.Row`. */
export interface SessionAttendanceRow {
  studentProfilePublicId: string | null;
  enrollmentPublicId: string | null;
  studentNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  recordedAt: string;
  source: AttendanceSource;
}

/** `SessionAttendanceResponse`. */
export interface SessionAttendanceResponse {
  sessionPublicId: string;
  checkpointPublicId: string | null;
  expectedCount: number;
  presentCount: number;
  records: SessionAttendanceRow[];
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

/** Rôles autorisés à ouvrir / fermer / émettre un jeton (`CourseSessionWeb.MANAGE_ROLES`). */
export const SESSION_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
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
