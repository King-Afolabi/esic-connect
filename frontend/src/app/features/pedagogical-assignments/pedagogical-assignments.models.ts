/**
 * Types du parcours « Responsables pédagogiques »
 * (`com.esic.connect.academic.internal.PedagogicalAssignmentController`).
 * Aucun champ, endpoint, statut HTTP ni code d'erreur n'est inventé.
 *
 * - `GET  /pedagogical-assignments`             → `PageResponse<PedagogicalAssignmentResponse>`
 * - `GET  /pedagogical-assignments/{publicId}`  → `PedagogicalAssignmentResponse`
 * - `POST /pedagogical-assignments`             → `PedagogicalAssignmentResponse` (201)
 * - `POST /pedagogical-assignments/{publicId}/close` → 204
 *
 * Rôles : `ADMIN` / `SUPER_ADMIN` uniquement (`AcademicWeb.ASSIGNMENT_ROLES`)
 * — y compris pour la simple lecture ; ni `SCHOOL_ADMINISTRATION`, ni le
 * responsable pédagogique lui-même n'y ont accès.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** `PedagogicalAssignmentRole` — principal ou délégué. */
export const PEDAGOGICAL_ASSIGNMENT_TYPES = ['PRIMARY_MANAGER', 'DELEGATE'] as const;
export type PedagogicalAssignmentType = (typeof PEDAGOGICAL_ASSIGNMENT_TYPES)[number];

export const PEDAGOGICAL_ASSIGNMENT_TYPE_LABELS: Record<PedagogicalAssignmentType, string> = {
  PRIMARY_MANAGER: 'Responsable principal',
  DELEGATE: 'Délégué',
};
export function pedagogicalAssignmentTypeLabel(value: string): string {
  return (PEDAGOGICAL_ASSIGNMENT_TYPE_LABELS as Record<string, string>)[value] ?? value;
}

/** `PedagogicalAssignmentStatus`. */
export type PedagogicalAssignmentStatus = 'ACTIVE' | 'CLOSED';
export const PEDAGOGICAL_ASSIGNMENT_STATUS_LABELS: Record<PedagogicalAssignmentStatus, string> = {
  ACTIVE: 'Active',
  CLOSED: 'Clôturée',
};
export function pedagogicalAssignmentStatusLabel(value: string): string {
  return (PEDAGOGICAL_ASSIGNMENT_STATUS_LABELS as Record<string, string>)[value] ?? value;
}

/** `PedagogicalAssignmentResponse` — jamais d'identifiant SQL interne. */
export interface PedagogicalAssignmentResponse {
  publicId: string;
  programPublicId: string;
  programCode: string;
  userPublicId: string;
  type: PedagogicalAssignmentType;
  status: PedagogicalAssignmentStatus;
  /** `LocalDate` ISO-8601 (`aaaa-mm-jj`). */
  validFrom: string;
  validUntil: string | null;
  reason: string | null;
  closeReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `PedagogicalAssignmentRequests.Create`. */
export interface CreatePedagogicalAssignmentRequest {
  programPublicId: string;
  userPublicId: string;
  type: PedagogicalAssignmentType;
  /** `LocalDate` ISO-8601 ; obligatoire côté écran (le back-end accepte l'absence et retient aujourd'hui). */
  validFrom: string;
  validUntil?: string | null;
  reason?: string | null;
}

/** `PedagogicalAssignmentRequests.Close` — motif obligatoire. */
export interface ClosePedagogicalAssignmentRequest {
  reason: string;
  effectiveDate?: string | null;
}

export interface PedagogicalAssignmentListQuery {
  /** `program` — `public_id` d'une formation. */
  program?: string | null;
  /** `user` — `public_id` d'un compte responsable pédagogique. */
  user?: string | null;
  type?: PedagogicalAssignmentType | null;
  status?: PedagogicalAssignmentStatus | null;
  /** `activeOn` — `LocalDate` ISO-8601 ; affectations effectives ce jour-là. */
  activeOn?: string | null;
  sort?: string | null;
  page?: number;
  size?: number;
}
