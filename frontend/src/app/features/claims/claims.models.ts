/**
 * Types des réclamations (EF-CLAIM-001..004 ; docs/02 §20), strictement
 * alignés sur le contrat du module back-end `claim` — aucun champ,
 * statut, code d'erreur ni route n'est inventé ici.
 *
 * - `POST /api/v1/claims`                      → `ClaimResponse` (201)
 * - `GET  /api/v1/claims?audience=&page=&size=`→ `ClaimPageResponse`
 * - `GET  /api/v1/claims/{id}`                 → `ClaimThreadResponse`
 * - `POST /api/v1/claims/{id}/messages`        → `ClaimThreadResponse`
 * - `POST /api/v1/claims/{id}/transfer`        → `ClaimThreadResponse`
 * - `POST /api/v1/claims/{id}/decision`        → `ClaimThreadResponse`
 * - `POST /api/v1/claims/{id}/reopen`          → `ClaimThreadResponse`
 */

export const CLAIM_CATEGORIES = [
  'ATTENDANCE',
  'JUSTIFICATION',
  'SCHEDULE',
  'ACCOUNT',
  'OTHER',
] as const;
export type ClaimCategory = (typeof CLAIM_CATEGORIES)[number];
const CLAIM_CATEGORY_LABELS: Record<ClaimCategory, string> = {
  ATTENDANCE: 'Assiduité',
  JUSTIFICATION: 'Justificatif',
  SCHEDULE: 'Planning',
  ACCOUNT: 'Compte',
  OTHER: 'Autre',
};
export function claimCategoryLabel(value: string): string {
  return (CLAIM_CATEGORY_LABELS as Record<string, string>)[value] ?? value;
}

/**
 * Guichet destinataire. C'est une fonction, pas une personne : c'est
 * précisément ce qui rend le transfert possible (docs/02 §20.3).
 */
export const CLAIM_AUDIENCES = [
  'TEACHER',
  'PEDAGOGICAL_MANAGER',
  'SCHOOL_ADMINISTRATION',
] as const;
export type ClaimAudience = (typeof CLAIM_AUDIENCES)[number];
const CLAIM_AUDIENCE_LABELS: Record<ClaimAudience, string> = {
  TEACHER: 'Formateur',
  PEDAGOGICAL_MANAGER: 'Responsable pédagogique',
  SCHOOL_ADMINISTRATION: 'Administration scolaire',
};
export function claimAudienceLabel(value: string): string {
  return (CLAIM_AUDIENCE_LABELS as Record<string, string>)[value] ?? value;
}

export const CLAIM_STATUSES = [
  'OPEN',
  'IN_PROGRESS',
  'WAITING_FOR_STUDENT',
  'TRANSFERRED',
  'RESOLVED',
  'CLOSED',
  'REJECTED',
  'REOPENED',
] as const;
export type ClaimStatus = (typeof CLAIM_STATUSES)[number];
const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  OPEN: 'Ouverte',
  IN_PROGRESS: 'En cours de traitement',
  WAITING_FOR_STUDENT: "En attente de l'apprenant",
  TRANSFERRED: 'Transférée',
  RESOLVED: 'Résolue',
  CLOSED: 'Clôturée',
  REJECTED: 'Rejetée',
  REOPENED: 'Rouverte',
};
export function claimStatusLabel(value: string): string {
  return (CLAIM_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** Statuts sur lesquels le fil est clos : plus de message, réouverture possible. */
export const CLOSED_CLAIM_STATUSES: readonly ClaimStatus[] = [
  'RESOLVED',
  'CLOSED',
  'REJECTED',
];

/** Décisions posables directement — `TRANSFERRED` et `REOPENED` ont leurs propres routes. */
export const CLAIM_DECISIONS = [
  'IN_PROGRESS',
  'WAITING_FOR_STUDENT',
  'RESOLVED',
  'CLOSED',
  'REJECTED',
] as const;

export interface ClaimSummary {
  publicId: string;
  authorPublicId: string;
  category: string;
  subject: string;
  status: ClaimStatus;
  audience: string;
  sessionPublicId: string | null;
  classGroupPublicId: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimMessageView {
  publicId: string;
  authorPublicId: string;
  /** Rôle employé au moment du message — figé à l'écriture. */
  authorRole: string;
  body: string;
  createdAt: string;
}

export interface ClaimEventView {
  publicId: string;
  eventType: string;
  fromStatus: string | null;
  toStatus: string | null;
  fromAudience: string | null;
  toAudience: string | null;
  motive: string | null;
  createdAt: string;
}

export interface ClaimThread {
  claim: ClaimSummary;
  messages: ClaimMessageView[];
  events: ClaimEventView[];
}

export interface ClaimPage {
  content: ClaimSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateClaimRequest {
  category: string;
  subject: string;
  description: string;
  audience: string;
  sessionPublicId?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
}

const CLAIM_EVENT_LABELS: Record<string, string> = {
  CREATED: 'Réclamation ouverte',
  STATUS_CHANGED: 'Statut modifié',
  TRANSFERRED: 'Transférée',
  REOPENED: 'Rouverte',
};
export function claimEventLabel(value: string): string {
  return CLAIM_EVENT_LABELS[value] ?? value;
}

const CLAIM_ROLE_LABELS: Record<string, string> = {
  STUDENT: 'Apprenant',
  TEACHER: 'Formateur',
  PEDAGOGICAL_MANAGER: 'Responsable pédagogique',
  SCHOOL_ADMINISTRATION: 'Administration scolaire',
  ADMIN: 'Administration',
  SUPER_ADMIN: 'Administration technique',
};
export function claimRoleLabel(value: string): string {
  return CLAIM_ROLE_LABELS[value] ?? value;
}

/** Rôles qui traitent une réclamation, par opposition à celui qui la dépose. */
export const CLAIM_STAFF_ROLES = [
  'TEACHER',
  'PEDAGOGICAL_MANAGER',
  'SCHOOL_ADMINISTRATION',
  'ADMIN',
  'SUPER_ADMIN',
] as const;
