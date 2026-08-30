/**
 * Types de la gestion de l'assiduité (V10), strictement alignés sur le
 * contrat des modules back-end `attendance` et `coursesession` — aucun
 * champ, endpoint, statut HTTP, rôle ni code `ATT_*` n'est inventé.
 *
 * Espace apprenant (`STUDENT`) :
 * - `GET  /api/v1/me/attendance`                         → `PageResponse<MyAttendanceRow>`
 * - `GET  /api/v1/me/attendance/{id}`                    → `MyAttendanceDetail`
 * - `POST /api/v1/me/attendance/justifications`          → `JustificationResponse` (201)
 * - `PUT  /api/v1/me/attendance/justifications/{id}`     → `JustificationResponse`
 * - `GET  /api/v1/me/attendance/justifications`          → `JustificationResponse[]`
 * - `GET  /api/v1/me/attendance/justifications/{id}`     → `JustificationResponse`
 *
 * Gestion (staff) :
 * - `GET  /api/v1/attendance/justifications?status=`     → `JustificationResponse[]`
 * - `GET  /api/v1/attendance/justifications/{id}`        → `JustificationResponse`
 * - `POST /api/v1/attendance/justifications/{id}/review` → `JustificationResponse`
 * - `GET  /api/v1/attendance/reports/{sessions|classes|students}` → `PageResponse<…>`
 * - `GET  /api/v1/attendance/reports/summary`            → `SummaryResponse`
 * - `GET  /api/v1/attendance/reports/{…}/export`         → `text/csv` (blob)
 */

import {
  AttendanceStatus,
  CheckpointType,
  PageResponse,
} from '../sessions/sessions.models';

export type { PageResponse } from '../sessions/sessions.models';

// ---------------------------------------------------------------------------
// Justificatifs
// ---------------------------------------------------------------------------

export const JUSTIFICATION_CATEGORIES = [
  'MEDICAL',
  'TRANSPORT',
  'FAMILY',
  'ADMINISTRATIVE',
  'OTHER',
] as const;
export type JustificationCategory = (typeof JUSTIFICATION_CATEGORIES)[number];
export const JUSTIFICATION_CATEGORY_LABELS: Record<JustificationCategory, string> = {
  MEDICAL: 'Médical',
  TRANSPORT: 'Transport',
  FAMILY: 'Familial',
  ADMINISTRATIVE: 'Administratif',
  OTHER: 'Autre',
};
export function justificationCategoryLabel(value: string): string {
  return (JUSTIFICATION_CATEGORY_LABELS as Record<string, string>)[value] ?? value;
}

export const JUSTIFICATION_STATUSES = ['PENDING', 'ACCEPTED', 'REJECTED'] as const;
export type JustificationStatus = (typeof JUSTIFICATION_STATUSES)[number];
export const JUSTIFICATION_STATUS_LABELS: Record<JustificationStatus, string> = {
  PENDING: 'En attente',
  ACCEPTED: 'Accepté',
  REJECTED: 'Refusé',
};
export function justificationStatusLabel(value: string): string {
  return (JUSTIFICATION_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** `JustificationResponse` — les champs d'identité nominative ne sont renseignés que côté gestion. */
export interface JustificationResponse {
  publicId: string;
  status: JustificationStatus;
  category: JustificationCategory;
  externalReference: string | null;
  comment: string;
  submittedAt: string;
  reviewedAt: string | null;
  decisionReason: string | null;
  sessionPublicId: string | null;
  sessionTitle: string | null;
  sessionStartsAt: string | null;
  checkpointPublicId: string | null;
  checkpointLabel: string | null;
  classCode: string | null;
  studentProfilePublicId: string | null;
  studentNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  attendanceStatus: AttendanceStatus;
}

/** `JustificationRequests.Submit`. */
export interface SubmitJustificationRequest {
  checkpointPublicId: string;
  category: JustificationCategory;
  externalReference?: string | null;
  comment: string;
}

/** `JustificationRequests.Amend`. */
export interface AmendJustificationRequest {
  category: JustificationCategory;
  externalReference?: string | null;
  comment: string;
}

/** `JustificationRequests.Review`. */
export interface ReviewJustificationRequest {
  decision: 'ACCEPTED' | 'REJECTED';
  decisionReason?: string | null;
}

// ---------------------------------------------------------------------------
// Espace apprenant « Mes présences »
// ---------------------------------------------------------------------------

/** `MyAttendanceRow`. `status` peut aussi valoir `OPEN` / `PLANNED` (point de contrôle non fermé). */
export interface MyAttendanceRow {
  attendancePublicId: string | null;
  sessionPublicId: string;
  sessionTitle: string | null;
  sessionStartsAt: string;
  checkpointPublicId: string;
  checkpointLabel: string;
  checkpointType: CheckpointType;
  checkpointRequired: boolean;
  classCode: string | null;
  status: string;
  lateMinutes: number | null;
  comment: string | null;
  recordedAt: string | null;
  justificationPublicId: string | null;
  justificationStatus: JustificationStatus | null;
  canJustify: boolean;
}

/** `AttendanceCorrectionResponse` (réexposé pour l'espace apprenant). */
export interface AttendanceHistoryEntry {
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

/** `MyAttendanceDetail`. */
export interface MyAttendanceDetail {
  row: MyAttendanceRow;
  history: AttendanceHistoryEntry[];
  justification: JustificationResponse | null;
}

export interface MyAttendanceQuery {
  from?: string | null;
  to?: string | null;
  status?: string | null;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Rapports
// ---------------------------------------------------------------------------

/** `AttendanceReports.SessionRow`. */
export interface SessionReportRow {
  sessionPublicId: string;
  sessionTitle: string | null;
  startsAt: string;
  endsAt: string;
  classCodes: string;
  teacherName: string;
  checkpointCount: number;
  expectedCount: number;
  presentCount: number;
  lateCount: number;
  absentCount: number;
  excusedCount: number;
  attendanceRate: number;
}

/** `AttendanceReports.HalfDayTotals`. */
export interface HalfDayTotals {
  expectedHalfDays: number;
  presentHalfDays: number;
  absentHalfDays: number;
  excusedHalfDays: number;
  companyHalfDays: number;
  unknownHalfDays: number;
  lateCount: number;
  attendanceRate: number;
  unjustifiedAbsenceRate: number;
}

/** `AttendanceReports.ClassRow`. */
export interface ClassReportRow {
  classGroupPublicId: string;
  classCode: string;
  studentCount: number;
  totals: HalfDayTotals;
}

/** `AttendanceReports.StudentRow`. */
export interface StudentReportRow {
  studentProfilePublicId: string;
  enrollmentPublicId: string;
  studentNumber: string | null;
  firstName: string | null;
  lastName: string | null;
  classCode: string | null;
  totals: HalfDayTotals;
}

/** `AttendanceReports.Summary`. */
export interface SummaryResponse {
  from: string | null;
  to: string | null;
  classCount: number;
  sessionCount: number;
  totals: HalfDayTotals;
  pendingJustifications: number;
  notes: string[];
}

export interface ReportQuery {
  from?: string | null;
  to?: string | null;
  classGroup?: string | null;
  studentProfile?: string | null;
  /** `field,asc` | `field,desc` — cf. {@link REPORT_SORT_FIELDS}. */
  sort?: string | null;
  page?: number;
  size?: number;
}

export type ReportKind = 'sessions' | 'classes' | 'students';

/**
 * Liste blanche du tri serveur (correctif PR #22 §6), alignée sur
 * `AttendanceReportSort` côté back-end. Le composant n'émet jamais
 * d'autre valeur ; un `sort` hors liste renverrait
 * `400 ATT_REPORT_INVALID_SORT`.
 */
export const REPORT_SORT_FIELDS: Record<ReportKind, readonly string[]> = {
  sessions: ['startsAt', 'attendanceRate', 'presentCount'],
  classes: ['classCode', 'attendanceRate', 'absentHalfDays'],
  students: ['lastName', 'studentNumber', 'attendanceRate', 'absentHalfDays'],
};

export interface ReportSortOption {
  value: string;
  label: string;
}

const SORT_FIELD_LABELS: Record<string, string> = {
  startsAt: 'Date de début',
  attendanceRate: 'Taux de présence',
  presentCount: 'Présences',
  classCode: 'Code de classe',
  absentHalfDays: 'Demi-journées absentes',
  lastName: 'Nom',
  studentNumber: 'Numéro étudiant',
};

/** Options `mat-select` de tri pour un rapport donné (défaut + asc/desc). */
export function reportSortOptions(kind: ReportKind): ReportSortOption[] {
  const options: ReportSortOption[] = [{ value: '', label: 'Tri par défaut' }];
  for (const field of REPORT_SORT_FIELDS[kind]) {
    const name = SORT_FIELD_LABELS[field] ?? field;
    options.push({ value: `${field},asc`, label: `${name} (croissant)` });
    options.push({ value: `${field},desc`, label: `${name} (décroissant)` });
  }
  return options;
}

/** Vrai si `sort` respecte la liste blanche du `kind` (défense en profondeur). */
export function isAllowedReportSort(kind: ReportKind, sort: string | null | undefined): boolean {
  if (!sort) {
    return true;
  }
  const [field, direction, ...rest] = sort.split(',');
  return (
    rest.length === 0 &&
    (direction === 'asc' || direction === 'desc') &&
    REPORT_SORT_FIELDS[kind].includes(field)
  );
}

// ---------------------------------------------------------------------------
// Rôles (ergonomie ; Spring Security reste l'autorité)
// ---------------------------------------------------------------------------

/** Rôles autorisés à consulter les rapports et à examiner les justificatifs. */
export const ATTENDANCE_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;

/** Pourcentage lisible d'un ratio [0..1]. */
export function percent(ratio: number | null | undefined): string {
  if (ratio === null || ratio === undefined || Number.isNaN(ratio)) {
    return '—';
  }
  return `${(ratio * 100).toFixed(1).replace('.', ',')} %`;
}

export type { AttendanceStatus, CheckpointType } from '../sessions/sessions.models';
export type PageResponseOf<T> = PageResponse<T>;
