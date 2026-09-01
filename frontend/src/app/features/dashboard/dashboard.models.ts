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

export interface DashboardManagerCard {
  classCount: number;
  upcomingSessions: DashboardSessionLine[];
  classCodes: string[];
}

export interface DashboardAdministrationCard {
  activeAccounts: number;
  suspendedAccounts: number;
  pendingActivation: number;
  archivedAccounts: number;
  pendingJustifications: number;
  recentImports: DashboardImportLine[];
  todaySessions: DashboardSessionLine[];
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
