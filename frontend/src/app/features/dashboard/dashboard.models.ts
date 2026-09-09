/**
 * Types du tableau de bord par rôle (bloc G1-F), miroir exact du DTO
 * back-end `com.esic.connect.dashboard.internal.DashboardResponses` —
 * aucun champ, endpoint ni rôle n'est inventé.
 *
 * Route : `GET /api/v1/me/dashboard` (`@PreAuthorize("isAuthenticated()")`).
 * Le rôle effectif et le périmètre sont décidés **côté serveur** ; une
 * seule des sections `student` / `teacher` / `manager` / `administration`
 * est renseignée.
 */

export type DashboardRole =
  | 'STUDENT'
  | 'TEACHER'
  | 'PEDAGOGICAL_MANAGER'
  | 'ADMINISTRATION';

export interface DashboardSessionLine {
  sessionPublicId: string;
  title: string | null;
  status: string;
  startsAt: string;
  endsAt: string;
  classCodes: string[];
}

export interface DashboardImportLine {
  publicId: string;
  status: string;
  totalRows: number;
  createdAt: string;
}

export interface DashboardStudentCard {
  nextSession: DashboardSessionLine | null;
  weekSessions: DashboardSessionLine[];
  present: number;
  late: number;
  absent: number;
  excused: number;
  pendingJustifications: number;
  rejectedJustifications: number;
}

export interface DashboardTeacherCard {
  nextSession: DashboardSessionLine | null;
  upcoming: DashboardSessionLine[];
  toOpen: DashboardSessionLine[];
}

/**
 * Ligne de taux d'assiduité (EF-REP-007, EF-REP-008).
 *
 * C'est **la même source** qui alimente le graphique et le tableau
 * équivalent : le cahier exige qu'un graphique dispose « d'un tableau
 * équivalent » (§22.6), et deux sources de données finiraient par
 * diverger.
 */
export interface DashboardAttendanceRateLine {
  label: string;
  expectedHalfDays: number;
  presentHalfDays: number;
  absentHalfDays: number;
  excusedHalfDays: number;
  lateCount: number;
  /** Entre 0 et 1. */
  attendanceRate: number;
}

export interface DashboardAuditLine {
  occurredAt: string;
  actor: string | null;
  action: string;
  result: string;
}

export interface DashboardManagerCard {
  classCount: number;
  upcomingSessions: DashboardSessionLine[];
  classCodes: string[];
  periodFrom: string;
  periodTo: string;
  attendanceRate: number;
  lateCount: number;
  unjustifiedAbsenceHalfDays: number;
  classRates: DashboardAttendanceRateLine[];
  pendingJustifications: number;
  openClaims: number;
  pendingActivations: number;
}

export interface DashboardAdministrationCard {
  activeAccounts: number;
  suspendedAccounts: number;
  pendingActivation: number;
  archivedAccounts: number;
  expiredInvitations: number;
  pendingJustifications: number;
  decidedJustifications: number;
  /** `null` si rien n'a été traité — « 0 h » ferait croire à l'inverse. */
  medianDecisionDelayHours: number | null;
  periodFrom: string;
  periodTo: string;
  globalAttendanceRate: number;
  programRates: DashboardAttendanceRateLine[];
  recentImports: DashboardImportLine[];
  todaySessions: DashboardSessionLine[];
  recentExports: DashboardAuditLine[];
  recentAuditOperations: DashboardAuditLine[];
}

export interface DashboardResponse {
  role: DashboardRole;
  generatedAt: string;
  student: DashboardStudentCard | null;
  teacher: DashboardTeacherCard | null;
  manager: DashboardManagerCard | null;
  administration: DashboardAdministrationCard | null;
  notes: string[];
}

/** `Instant` ISO-8601 → `jj/mm hh:mm`. `—` si absent / illisible. */
export function shortInstant(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day}/${month} ${hh}:${mm}`;
}

/** Taux 0–1 → pourcentage lisible, deux décimales. */
export function percent(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) {
    return '—';
  }
  return `${(rate * 100).toFixed(2)} %`;
}

/** Largeur de barre d'un histogramme, bornée à [0, 100]. */
export function barWidth(rate: number | null | undefined): number {
  if (rate === null || rate === undefined || Number.isNaN(rate)) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round(rate * 1000) / 10));
}

/** `Instant` ISO-8601 → `jj/mm/aaaa`. `—` si absent / illisible. */
export function shortDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}
