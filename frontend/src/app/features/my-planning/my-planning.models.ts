/**
 * Vue API de `GET /api/v1/me/planning`
 * (`com.esic.connect.myplanning.internal.MyPlanningResponses`).
 */
export interface MyPlanningTeacher {
  publicId: string | null;
  firstName: string | null;
  lastName: string | null;
}

export interface MyPlanningClass {
  publicId: string;
  code: string | null;
}

export interface MyPlanningSession {
  sessionPublicId: string;
  title: string | null;
  status: string;
  startsAt: string;
  endsAt: string;
  timeZoneId: string;
  teacher: MyPlanningTeacher;
  classes: MyPlanningClass[];
  roomCode: string | null;
}

export interface MyPlanning {
  /** `'TEACHER'` ou `'STUDENT'` — le rôle sous lequel le serveur a bâti la réponse. */
  role: 'TEACHER' | 'STUDENT' | 'NONE';
  sessions: MyPlanningSession[];
}

const SESSION_STATUS_LABELS: Record<string, string> = {
  PLANNED: 'Prévue',
  OPEN: 'En cours',
  CLOSED: 'Terminée',
  CANCELLED: 'Annulée',
};

export function sessionStatusLabel(status: string): string {
  return SESSION_STATUS_LABELS[status] ?? status;
}

/** Nom civil du formateur, ou repli neutre si non résolu. */
export function teacherDisplayName(teacher: MyPlanningTeacher): string {
  const name = [teacher.firstName, teacher.lastName].filter(Boolean).join(' ').trim();
  return name || 'Formateur';
}
