/**
 * Rôles système, alignés sur `RoleCode` du back-end
 * (docs/02-cahier-des-charges.md §7 ; `com.esic.connect.identity.internal.RoleCode`).
 *
 * Ces valeurs ne servent QU'À l'affichage et à la navigation côté client.
 * L'autorisation réelle reste décidée par Spring Security (docs/07 §7).
 */
export const ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
  'STUDENT',
] as const;

export type Role = (typeof ROLES)[number];

/** Libellés lisibles pour l'interface (français, docs/02 §38.7). */
export const ROLE_LABELS: Record<Role, string> = {
  SUPER_ADMIN: 'Super administrateur',
  ADMIN: 'Administrateur',
  SCHOOL_ADMINISTRATION: 'Administration scolaire',
  PEDAGOGICAL_MANAGER: 'Responsable pédagogique',
  TEACHER: 'Formateur',
  STUDENT: 'Apprenant',
};

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

export function roleLabel(role: string): string {
  return isRole(role) ? ROLE_LABELS[role] : role;
}
