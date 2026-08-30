/**
 * Types de l'administration des **comptes utilisateurs et de leurs
 * rôles**, strictement alignés sur le contrat du module back-end
 * `identity` (`com.esic.connect.identity.internal`) :
 *
 * - `GET  /api/v1/users`                            → `PageResponse<UserSummaryResponse>`
 * - `GET  /api/v1/users/{publicId}`                 → `UserDetailResponse`
 * - `POST /api/v1/users/{publicId}/suspend`         ← `AccountActionRequest`  → 204
 * - `POST /api/v1/users/{publicId}/restore`         ← `AccountActionRequest`  → 204
 * - `POST /api/v1/users/{publicId}/archive`         ← `AccountActionRequest`  → 204
 * - `POST /api/v1/users/{publicId}/roles`           ← `AssignRoleRequest`     → 204
 * - `POST /api/v1/users/{publicId}/roles/{roleCode}/revoke` ← `AccountActionRequest` → 204
 *
 * Aucun champ n'est inventé : chaque propriété correspond à un composant
 * du `record` Java associé. Les routes de **lecture** sont réservées
 * côté serveur à `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`
 * (`UserAccountController` `READ_ROLES`) ; la suspension et la
 * réactivation partagent ce périmètre, l'archivage et la gestion de
 * rôle sont limités à `ADMIN` / `SUPER_ADMIN`. Le back-end applique en
 * plus des gardes fines (protection `SUPER_ADMIN`, auto-action,
 * dernier rôle actif) : le front n'anticipe que les cas manifestement
 * inutiles et laisse Spring Security décider.
 */

import { Role } from '../../core/models/role';

/** `AccountStatus` (docs/02-cahier-des-charges.md §9.4). */
export const ACCOUNT_STATUSES = [
  'PENDING_ACTIVATION',
  'ACTIVE',
  'SUSPENDED',
  'LOCKED',
  'ARCHIVED',
] as const;
export type AccountStatus = (typeof ACCOUNT_STATUSES)[number];

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING_ACTIVATION: "En attente d'activation",
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  LOCKED: 'Verrouillé',
  ARCHIVED: 'Archivé',
};

export function accountStatusLabel(status: string): string {
  return (ACCOUNT_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/**
 * Enveloppe de pagination stable (`PageResponse<T>` côté serveur ; le
 * format JSON par défaut de `Page` est explicitement déconseillé).
 */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Vue liste d'un compte — `UserSummaryResponse`. */
export interface UserSummaryResponse {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  status: AccountStatus;
  /** Codes des rôles **actifs** (`RoleCode`). */
  roles: string[];
  /** `Instant` ISO-8601. */
  createdAt: string;
  /** `Instant` ISO-8601 ou `null`. */
  lastLoginAt: string | null;
}

/**
 * Une affectation de rôle, active ou clôturée — `RoleAssignmentResponse`.
 * L'historique complet est conservé (docs/02 §9.7).
 */
export interface RoleAssignmentResponse {
  role: string;
  active: boolean;
  validFrom: string;
  validUntil: string | null;
}

/** Vue détaillée d'un compte — `UserDetailResponse`. */
export interface UserDetailResponse {
  publicId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  status: AccountStatus;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  suspendedAt: string | null;
  suspensionReason: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Historique complet des rôles, du plus récent au plus ancien. */
  roleAssignments: RoleAssignmentResponse[];
}

/**
 * Champs de tri réellement acceptés par `GET /api/v1/users`
 * (liste blanche `UserManagementService.SORTABLE_FIELDS` ; toute autre
 * valeur → 400 `USER_INVALID_SORT`). Le tri par défaut du service est
 * `createdAt,desc`.
 */
export const USER_SORT_FIELDS = ['createdAt', 'lastLoginAt', 'email', 'lastName'] as const;
export type UserSortField = (typeof USER_SORT_FIELDS)[number];

export type SortDirection = 'asc' | 'desc';

/**
 * Rôles proposés comme filtre `role` : l'ensemble de `RoleCode`
 * (le back-end filtre sur une affectation **active**). Réutilise la
 * source unique `Role` du cœur applicatif — aucune valeur inventée.
 */
export type UserRoleFilter = Role;

/**
 * Longueur maximale du motif d'une opération de cycle de vie ou de
 * retrait de rôle (`AccountActionRequest` — `@Size(max = 500)`).
 */
export const ACTION_REASON_MAX_LENGTH = 500;

/**
 * Corps commun des mutations de cycle de vie (suspension, réactivation,
 * archivage) et du retrait d'un rôle — `AccountActionRequest`. Le motif
 * est obligatoire (`@NotBlank`) et alimente la piste d'audit.
 */
export interface AccountActionRequest {
  reason: string;
}

/**
 * Corps d'une attribution de rôle — `AssignRoleRequest`. `role` et
 * `reason` sont tous deux obligatoires (`@NotBlank`) ; un code de rôle
 * inconnu produit `400 USER_ROLE_UNKNOWN` côté serveur.
 */
export interface AssignRoleRequest {
  role: Role;
  reason: string;
}

/** Paramètres de `GET /api/v1/users` réellement exposés. */
export interface UserListQuery {
  /** `q` — sous-chaîne insensible à la casse sur email / prénom / nom. */
  q?: string | null;
  status?: AccountStatus | null;
  /** `role` — code d'un rôle ; filtre sur une affectation active. */
  role?: UserRoleFilter | null;
  /** `sort` — `champ` ou `champ,asc|desc`, champ dans la liste blanche. */
  sort?: string | null;
  page?: number;
  size?: number;
}
