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
  studentUserPublicId: string | null;
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
// Pièces jointes des justificatifs (bloc G1-E)
// ---------------------------------------------------------------------------

/**
 * `JustificationAttachmentResponses.Meta` — métadonnées d'une pièce
 * jointe `STORED`. Jamais de `storageKey`, de chemin ni d'identifiant
 * SQL. Routes :
 * - `POST   /api/v1/me/attendance/justifications/{id}/attachment`          → 201
 * - `GET    /api/v1/me/attendance/justifications/{id}/attachment`          → Meta | 404
 * - `GET    /api/v1/me/attendance/justifications/{id}/attachment/download` → blob
 * - `DELETE /api/v1/me/attendance/justifications/{id}/attachment`          → 204
 * - `GET    /api/v1/attendance/justifications/{id}/attachment[/download]`  (examinateur)
 */
export interface JustificationAttachmentMeta {
  publicId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploadedAt: string;
  /**
   * Verdict antivirus (EF-JUS-002). `NOT_SCANNED` signifie qu'aucune
   * analyse n'a eu lieu — l'interface doit le dire, jamais le taire ni
   * présenter la pièce comme saine.
   */
  scanStatus: AttachmentScanStatus;
  scannedAt: string | null;
}

export type AttachmentScanStatus = 'NOT_SCANNED' | 'CLEAN' | 'INFECTED' | 'UNAVAILABLE';
const ATTACHMENT_SCAN_LABELS: Record<AttachmentScanStatus, string> = {
  NOT_SCANNED: 'Non analysée',
  CLEAN: 'Analysée, aucune menace détectée',
  INFECTED: 'Menace détectée',
  UNAVAILABLE: 'Analyse indisponible',
};
export function attachmentScanLabel(value: string): string {
  return (ATTACHMENT_SCAN_LABELS as Record<string, string>)[value] ?? value;
}

/** Taille maximale d'une pièce jointe (CDC §43 RG-071 : 5 Mo). */
export const JUSTIFICATION_ATTACHMENT_MAX_BYTES = 5 * 1024 * 1024;
/** `accept` de l'input fichier (ergonomie ; le serveur re-vérifie les magic bytes). */
export const JUSTIFICATION_ATTACHMENT_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';
const ATTACHMENT_TYPES = new Set(['application/pdf', 'image/jpeg', 'image/png']);
const ATTACHMENT_EXTENSIONS = new Set(['pdf', 'jpg', 'jpeg', 'png']);

/** Contrôle client (ergonomie) : type déclaré + extension + taille. */
export function checkAttachmentFile(file: File): { ok: true } | { ok: false; reason: string } {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  if (!ATTACHMENT_EXTENSIONS.has(ext) || (file.type && !ATTACHMENT_TYPES.has(file.type))) {
    return { ok: false, reason: 'Format non autorisé : PDF, JPEG ou PNG uniquement.' };
  }
  if (file.size === 0) {
    return { ok: false, reason: 'Le fichier est vide.' };
  }
  if (file.size > JUSTIFICATION_ATTACHMENT_MAX_BYTES) {
    return { ok: false, reason: 'Le fichier dépasse la taille maximale autorisée (5 Mo).' };
  }
  return { ok: true };
}

/** Taille lisible (`1,2 Mo`). */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} o`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1).replace('.', ',')} Ko`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} Mo`;
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
  studentUserPublicId: string;
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
  student?: string | null;
  /** `field,asc` | `field,desc` — cf. {@link REPORT_SORT_FIELDS}. */
  sort?: string | null;
  page?: number;
  size?: number;
}

export type ReportKind = 'sessions' | 'classes' | 'students';

/**
 * Formats d'export d'un rapport (EF-REP-003, EF-REP-004, EF-REP-005).
 * Liste fermée, miroir de `ReportExportFormat` côté serveur : c'est lui
 * qui décide, un format inconnu produit un `400`.
 */
export type ReportExportFormat = 'csv' | 'xlsx' | 'pdf';

export const REPORT_EXPORT_FORMATS: readonly { value: ReportExportFormat; label: string }[] = [
  { value: 'csv', label: 'CSV' },
  { value: 'xlsx', label: 'Excel' },
  { value: 'pdf', label: 'PDF' },
];

/**
 * Ligne du registre des documents officiels émis (EF-REP-006).
 * Aucun contenu d'attestation : seulement de quoi la retrouver.
 */
export interface ReportDocumentSummary {
  publicId: string;
  documentId: string;
  documentType: string;
  subject: string | null;
  issuedAt: string;
  issuedBy: string;
  revoked: boolean;
}

/**
 * Vérification d'un identifiant de document (AC-033). Ne porte
 * **aucune donnée d'assiduité** : un tiers doit pouvoir constater
 * l'émission, pas lire le taux de présence de la personne.
 */
export interface ReportDocumentCheck {
  documentId: string;
  documentType: string;
  issuedAt: string;
  issuedBy: string;
  periodStart: string | null;
  periodEnd: string | null;
  revoked: boolean;
}

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
// Départ anticipé (EF-ATT-013 ; docs/02 §16.13)
// ---------------------------------------------------------------------------

export const EARLY_DEPARTURE_STATUSES = [
  'REQUESTED',
  'FORWARDED',
  'ACCEPTED',
  'REFUSED',
] as const;
export type EarlyDepartureStatus = (typeof EARLY_DEPARTURE_STATUSES)[number];
const EARLY_DEPARTURE_STATUS_LABELS: Record<EarlyDepartureStatus, string> = {
  REQUESTED: 'Signalé',
  FORWARDED: 'Transmis au responsable',
  ACCEPTED: 'Accepté',
  REFUSED: 'Refusé',
};
export function earlyDepartureStatusLabel(value: string): string {
  return (EARLY_DEPARTURE_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** Effet du dossier sur la journée — calculé par le serveur, jamais ici. */
export type EarlyDepartureEffect = 'PARTIAL' | 'EXCUSED_PARTIAL' | 'TO_CONFIRM';
const EARLY_DEPARTURE_EFFECT_LABELS: Record<EarlyDepartureEffect, string> = {
  PARTIAL: 'Journée incomplète, non excusée',
  EXCUSED_PARTIAL: 'Journée incomplète, excusée',
  TO_CONFIRM: 'À confirmer',
};
export function earlyDepartureEffectLabel(value: string): string {
  return (EARLY_DEPARTURE_EFFECT_LABELS as Record<string, string>)[value] ?? value;
}

const EARLY_DEPARTURE_OPINION_LABELS: Record<string, string> = {
  FAVOURABLE: 'Avis favorable',
  UNFAVOURABLE: 'Avis défavorable',
};
export function earlyDepartureOpinionLabel(value: string | null): string {
  return value ? (EARLY_DEPARTURE_OPINION_LABELS[value] ?? value) : 'Sans avis';
}

/** `EarlyDepartureResponse` du back-end. Les acteurs y sont désignés par leur rôle. */
export interface EarlyDeparture {
  publicId: string;
  sessionPublicId: string | null;
  sessionTitle: string | null;
  sessionStartsAt: string | null;
  enrollmentPublicId: string | null;
  classCode: string | null;
  departureAt: string;
  reason: string;
  status: EarlyDepartureStatus;
  effect: EarlyDepartureEffect;
  requestedAt: string;
  teacherOpinion: string | null;
  teacherOpinionComment: string | null;
  teacherOpinionAt: string | null;
  decidedByRole: string | null;
  decidedAt: string | null;
  decisionComment: string | null;
}

export interface DeclareEarlyDepartureRequest {
  sessionPublicId: string;
  departureAt: string;
  reason: string;
}

export interface ForwardEarlyDepartureRequest {
  opinion?: string | null;
  comment?: string | null;
}

export interface DecideEarlyDepartureRequest {
  accepted: boolean;
  comment: string;
}

// ---------------------------------------------------------------------------
// Journal de transparence (EF-ATT-014 ; docs/02 §5.7)
// ---------------------------------------------------------------------------

const TRANSPARENCY_EVENT_LABELS: Record<string, string> = {
  RECORDED: 'Émargement enregistré',
  CREATED_MANUALLY: 'Présence saisie',
  STATUS_CORRECTED: 'Présence corrigée',
  CANCELLED: 'Présence annulée',
  JUSTIFICATION_ADDED: 'Justificatif déposé',
  JUSTIFICATION_UPDATED: 'Justificatif modifié',
  JUSTIFICATION_REVIEWED: 'Justificatif examiné',
  EARLY_DEPARTURE_DECLARED: 'Départ anticipé signalé',
  EARLY_DEPARTURE_FORWARDED: 'Départ anticipé transmis',
  EARLY_DEPARTURE_DECIDED: 'Départ anticipé tranché',
};
export function transparencyEventLabel(value: string): string {
  return TRANSPARENCY_EVENT_LABELS[value] ?? value;
}

const ACTOR_ROLE_LABELS: Record<string, string> = {
  SELF: 'Vous',
  STUDENT: 'Un apprenant',
  TEACHER: 'Un formateur',
  PEDAGOGICAL_MANAGER: 'Le responsable pédagogique',
  SCHOOL_ADMINISTRATION: "L'administration",
  ADMIN: "L'administration",
  SUPER_ADMIN: "L'administration technique",
};
/** Le serveur ne transmet que la fonction de l'auteur — jamais son nom. */
export function actorRoleLabel(value: string | null): string {
  return value ? (ACTOR_ROLE_LABELS[value] ?? value) : 'Le système';
}

const CHANNEL_LABELS: Record<string, string> = {
  DYNAMIC_QR: 'QR dynamique',
  SHORT_CODE: 'Code court',
  MANUAL: 'Saisie manuelle',
  CORRECTION: 'Correction',
  REMOTE_QR: 'QR à distance',
  REMOTE_CODE: 'Code court à distance',
  ROOM_STATIC_QR: 'QR fixe de salle',
};
export function attendanceChannelLabel(value: string | null): string {
  return value ? (CHANNEL_LABELS[value] ?? value) : '—';
}

/** `TransparencyEntry` du back-end : une ligne du journal de l'apprenant. */
export interface TransparencyEntry {
  occurredAt: string;
  event: string;
  actorRole: string | null;
  channel: string | null;
  sessionPublicId: string | null;
  sessionTitle: string | null;
  sessionStartsAt: string | null;
  checkpointLabel: string | null;
  previousStatus: string | null;
  newStatus: string | null;
  previousLateMinutes: number | null;
  newLateMinutes: number | null;
  reason: string | null;
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
