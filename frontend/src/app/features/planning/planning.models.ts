/**
 * Types de l'import, du versionnement et de la publication d'un planning —
 * miroir **exact** des DTO du back-end
 * (`com.esic.connect.planning.internal.PlanningResponses`). Aucune
 * propriété inventée ; aucun identifiant SQL n'est exposé par l'API.
 *
 * Endpoints réels :
 * - `POST /api/v1/planning-imports` (multipart `file` + `classGroupPublicId`) → `JobResponse` (201)
 * - `GET  /api/v1/planning-imports/{id}` → `JobResponse`
 * - `GET  /api/v1/planning-imports/{id}/rows` → `PageResponse<RowResponse>`
 * - `POST /api/v1/planning-imports/{id}/publish` → `PublicationResponse` (200)
 * - `POST /api/v1/planning-imports/{id}/cancel` → 204
 * - `GET  /api/v1/planning/versions?classGroupPublicId=…` → `PageResponse<VersionResponse>`
 * - `GET  /api/v1/planning/versions/{id}` → `VersionDetailResponse`
 *
 * Routes réservées côté serveur à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` / `PEDAGOGICAL_MANAGER`
 * (`PlanningWeb.MANAGE_ROLES`) ; un `PEDAGOGICAL_MANAGER` est filtré par
 * périmètre (`AcademicScopeDirectory`) et ne voit que ses propres jobs.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type PlanningJobStatus = 'SIMULATED' | 'PUBLISHED' | 'CANCELLED' | 'EXPIRED' | 'FAILED';
export type PlanningRowStatus = 'VALID' | 'WARNING' | 'ERROR';
export type PlanningPlannedAction = 'ADDED' | 'MODIFIED' | 'UNCHANGED' | 'REMOVED' | 'CONFLICT';
export type PlanningIssueSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'BLOCKING';
export type PlanningVersionStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED';

export const PLANNING_JOB_STATUS_LABELS: Record<PlanningJobStatus, string> = {
  SIMULATED: 'Simulé',
  PUBLISHED: 'Publié',
  CANCELLED: 'Annulé',
  EXPIRED: 'Expiré',
  FAILED: 'Échec',
};

export const PLANNING_ACTION_LABELS: Record<PlanningPlannedAction, string> = {
  ADDED: 'Ajout',
  MODIFIED: 'Modification',
  UNCHANGED: 'Inchangé',
  REMOVED: 'Retrait',
  CONFLICT: 'Conflit',
};

export function planningJobStatusLabel(value: string): string {
  return (PLANNING_JOB_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

export function planningActionLabel(value: string): string {
  return (PLANNING_ACTION_LABELS as Record<string, string>)[value] ?? value;
}

/** `PlanningResponses.JobResponse`. */
export interface PlanningJobResponse {
  publicId: string;
  status: PlanningJobStatus;
  classGroupPublicId: string | null;
  academicYearPublicId: string | null;
  originalFileName: string;
  fileSizeBytes: number;
  csvSeparator: string;
  totalRows: number;
  validRows: number;
  warningRows: number;
  errorRows: number;
  addedRows: number;
  modifiedRows: number;
  unchangedRows: number;
  removedEntries: number;
  confirmable: boolean;
  simulatedAt: string;
  expiresAt: string;
  publishedAt: string | null;
  publishedVersionPublicId: string | null;
  failureReason: string | null;
  createdAt: string;
}

/** `PlanningResponses.IssueResponse`. */
export interface PlanningIssueResponse {
  severity: PlanningIssueSeverity;
  errorCode: string;
  columnName: string | null;
  receivedValue: string | null;
  message: string;
}

/** `PlanningResponses.RowResponse`. */
export interface PlanningRowResponse {
  publicId: string;
  rowNumber: number;
  slotKey: string | null;
  sessionDate: string | null;
  startTime: string | null;
  endTime: string | null;
  timeZoneId: string | null;
  title: string | null;
  teacherPublicId: string | null;
  roomCode: string | null;
  rowStatus: PlanningRowStatus;
  plannedAction: PlanningPlannedAction;
  resolvedStartsAt: string | null;
  resolvedEndsAt: string | null;
  issues: PlanningIssueResponse[];
}

/** `PlanningResponses.PublicationResponse`. */
export interface PlanningPublicationResponse {
  jobPublicId: string;
  versionPublicId: string;
  versionNumber: number;
  alreadyPublished: boolean;
}

/** `PlanningResponses.VersionResponse`. */
export interface PlanningVersionResponse {
  publicId: string;
  schedulePublicId: string;
  classGroupPublicId: string;
  academicYearPublicId: string;
  versionNumber: number;
  status: PlanningVersionStatus;
  entryCount: number;
  changeSummary: string | null;
  replacedByVersionPublicId: string | null;
  publishedAt: string | null;
  createdAt: string;
}

/**
 * `PlanningResponses.VersionEntryResponse`.
 *
 * - `publicId`     : identifiant de CETTE ligne de version (aléatoire) ;
 * - `slotPublicId` : identité **stable** du créneau à travers les
 *   versions (même valeur d'une version à la suivante — DEC-G1-002) ;
 * - `sessionPublicId` : séance `course_session` liée (peut être `null`).
 */
export interface PlanningVersionEntryResponse {
  publicId: string;
  slotPublicId: string;
  slotKey: string;
  title: string;
  startsAt: string;
  endsAt: string;
  timeZoneId: string;
  roomCode: string | null;
  sessionPublicId: string | null;
}

/** `PlanningResponses.VersionDetailResponse`. */
export interface PlanningVersionDetailResponse {
  version: PlanningVersionResponse;
  entries: PlanningVersionEntryResponse[];
}

export interface PlanningRowListQuery {
  sort?: string | null;
  page?: number;
  size?: number;
}

/**
 * Formate un `Instant` ISO en `jj/mm/aaaa HH:mm` (UTC — déterministe).
 * Renvoie `—` pour une valeur absente / illisible.
 */
export function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${pad(date.getUTCDate())}/${pad(date.getUTCMonth() + 1)}/${date.getUTCFullYear()} ${pad(
    date.getUTCHours(),
  )}:${pad(date.getUTCMinutes())}`;
}

/**
 * Correction d'une ligne de planning avant publication (EF-PLAN-003).
 *
 * <p>Un champ absent reste inchangé ; une valeur vide efface le champ.
 * La liste des colonnes corrigeables est fermée côté serveur : y ajouter
 * un nom ici ne l'ouvrirait pas.
 */
export type PlanningRowCorrection = Partial<
  Record<
    | 'slot_key'
    | 'session_date'
    | 'start_time'
    | 'end_time'
    | 'time_zone_id'
    | 'title'
    | 'teacher_public_id'
    | 'room_code',
    string
  >
>;

/**
 * `PlanningRollbackService.RollbackResult` — retour à une version
 * antérieure (EF-PLAN-008, AC-009).
 *
 * La version créée est **N+1** : `versionNumber` est donc toujours plus
 * grand que `restoredFromNumber`. Rien n'est effacé (RG-046).
 */
export interface PlanningRollbackResponse {
  versionPublicId: string;
  versionNumber: number;
  restoredFromPublicId: string;
  restoredFromNumber: number;
  entryCount: number;
}

// ---------------------------------------------------------------------
// Construction directe dans le calendrier (EF-PLAN-006)
// ---------------------------------------------------------------------

/**
 * `PlanningCalendarService.CalendarSlot`.
 *
 * `origin` distingue un créneau **publié** d'un créneau du **brouillon**
 * en cours : les deux ne doivent jamais être mélangés à l'écran, sous
 * peine de laisser croire qu'un brouillon fait déjà foi.
 */
export interface PlanningCalendarSlot {
  publicId: string;
  slotKey: string | null;
  day: string;
  startsAt: string | null;
  endsAt: string | null;
  timeZoneId: string | null;
  title: string | null;
  teacherPublicId: string | null;
  roomCode: string | null;
  origin: 'PUBLISHED' | 'DRAFT';
  rowStatus: PlanningRowStatus | null;
}

/** `PlanningCalendarService.CalendarView`. */
export interface PlanningCalendarView {
  classGroupPublicId: string;
  from: string;
  to: string;
  publishedVersionNumber: number | null;
  draftJobPublicId: string | null;
  published: PlanningCalendarSlot[];
  draft: PlanningCalendarSlot[];
}

/** Corps de `POST /api/v1/planning/slots` et du PATCH d'un créneau. */
export interface PlanningSlotRequest {
  slotKey?: string | null;
  sessionDate: string;
  startTime: string;
  endTime: string;
  timeZoneId: string;
  title: string;
  teacherPublicId?: string | null;
  roomCode?: string | null;
}
