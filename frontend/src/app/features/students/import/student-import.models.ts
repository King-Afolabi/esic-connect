/**
 * Types de l'import CSV contrôlé des apprenants — miroir **exact** des
 * DTO du back-end (`com.esic.connect.studentimport.internal.StudentImportResponses`,
 * rapport §8). Aucune propriété inventée ; aucun identifiant SQL n'est
 * exposé par l'API.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type JobStatus = 'SIMULATED' | 'APPLIED' | 'CANCELLED' | 'EXPIRED';

export type IssueSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'BLOCKING';

export type RowStatus = 'VALID' | 'WARNING' | 'ERROR';

export type PlannedAction =
  | 'CREATE_ACCOUNT_AND_ENROLL'
  | 'ENROLL_EXISTING'
  | 'UPDATE_PROFILE'
  | 'TRANSFER_CLASS'
  | 'NONE';

export interface JobIssueResponse {
  severity: IssueSeverity;
  code: string;
  message: string;
  columnName: string | null;
}

export interface JobSummary {
  total: number;
  valid: number;
  warning: number;
  error: number;
  blocking: number;
  plannedCreate: number;
  plannedUpdate: number;
  plannedTransfer: number;
  plannedNoop: number;
}

export interface AppliedSummary {
  created: number | null;
  updated: number | null;
  transferred: number | null;
  invited: number | null;
  ignored: number | null;
}

export interface JobResponse {
  publicId: string;
  status: JobStatus;
  fileName: string;
  fileSha256: string;
  fileSizeBytes: number;
  csvSeparator: string;
  scopeProgramCode: string | null;
  scopeClassCode: string | null;
  confirmable: boolean;
  summary: JobSummary;
  issues: JobIssueResponse[];
  simulatedAt: string;
  expiresAt: string;
  confirmedAt: string | null;
  appliedSummary: AppliedSummary | null;
  createdAt: string;
}

export interface RowIssueResponse {
  severity: IssueSeverity;
  code: string;
  message: string;
  columnName: string | null;
  receivedValue: string | null;
  suggestedValue: string | null;
}

export interface RowResponse {
  publicId: string;
  rowNumber: number;
  rowStatus: RowStatus;
  plannedAction: PlannedAction;
  lastName: string | null;
  firstName: string | null;
  email: string | null;
  phone: string | null;
  formationCode: string | null;
  classCode: string | null;
  academicYear: string | null;
  studentNumber: string | null;
  birthDate: string | null;
  workStudy: boolean | null;
  companyName: string | null;
  resolvedClassPublicId: string | null;
  resolvedUserPublicId: string | null;
  resolvedEnrollmentPublicId: string | null;
  studentNumberGenerated: boolean;
  appliedOutcome: string | null;
  issues: RowIssueResponse[];
}

export interface ConfirmationResultResponse {
  jobPublicId: string;
  alreadyApplied: boolean;
  created: number;
  updated: number;
  transferred: number;
  invited: number;
  ignored: number;
}

export interface JobListQuery {
  status?: JobStatus | null;
  sort?: string | null;
  page?: number | null;
  size?: number | null;
}

export interface RowListQuery {
  rowStatus?: RowStatus | null;
  severity?: IssueSeverity | null;
  action?: PlannedAction | null;
  sort?: string | null;
  page?: number | null;
  size?: number | null;
}

/** Taille maximale acceptée côté client (le serveur reste l'autorité). */
export const MAX_CSV_BYTES = 2 * 1024 * 1024;

export const JOB_STATUSES: readonly JobStatus[] = ['SIMULATED', 'APPLIED', 'CANCELLED', 'EXPIRED'];

export const ROW_STATUSES: readonly RowStatus[] = ['VALID', 'WARNING', 'ERROR'];

export const ISSUE_SEVERITIES: readonly IssueSeverity[] = ['INFO', 'WARNING', 'ERROR', 'BLOCKING'];

export const PLANNED_ACTIONS: readonly PlannedAction[] = [
  'CREATE_ACCOUNT_AND_ENROLL',
  'ENROLL_EXISTING',
  'UPDATE_PROFILE',
  'TRANSFER_CLASS',
  'NONE',
];

const PLANNED_ACTION_LABELS: Record<PlannedAction, string> = {
  CREATE_ACCOUNT_AND_ENROLL: 'Créer le compte + inscrire',
  ENROLL_EXISTING: 'Inscrire un compte existant',
  UPDATE_PROFILE: 'Mettre à jour le profil',
  TRANSFER_CLASS: 'Changer de classe',
  NONE: 'Aucun changement',
};

export function plannedActionLabel(action: PlannedAction): string {
  return PLANNED_ACTION_LABELS[action] ?? action;
}

const ROW_STATUS_LABELS: Record<RowStatus, string> = {
  VALID: 'Valide',
  WARNING: 'Avertissement',
  ERROR: 'Erreur',
};

export function rowStatusLabel(status: RowStatus): string {
  return ROW_STATUS_LABELS[status] ?? status;
}

const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  SIMULATED: 'Simulé',
  APPLIED: 'Appliqué',
  CANCELLED: 'Annulé',
  EXPIRED: 'Expiré',
};

export function jobStatusLabel(status: JobStatus): string {
  return JOB_STATUS_LABELS[status] ?? status;
}

const SEVERITY_LABELS: Record<IssueSeverity, string> = {
  INFO: 'Information',
  WARNING: 'Avertissement',
  ERROR: 'Erreur',
  BLOCKING: 'Bloquant',
};

export function severityLabel(severity: IssueSeverity): string {
  return SEVERITY_LABELS[severity] ?? severity;
}
