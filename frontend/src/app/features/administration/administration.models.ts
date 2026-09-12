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
 *
 * Ne s'applique **pas** à l'attribution de rôle : `AssignRoleRequest.reason`
 * ne porte que `@NotBlank`, sans borne de longueur contractuelle.
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
 * `reason` sont tous deux obligatoires (`@NotBlank` uniquement — aucune
 * longueur maximale n'est imposée au motif) ; un code de rôle inconnu
 * produit `400 USER_ROLE_UNKNOWN` côté serveur.
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

/**
 * Création d'un compte (EF-USER-001).
 *
 * <p>Aucun champ de mot de passe, et il ne doit jamais y en avoir : le
 * compte naît en `PENDING_ACTIVATION` et la personne choisit son mot de
 * passe via son lien d'invitation (docs/02 §11.2).
 *
 * <p>`sendInvitation` vaut `true` par défaut côté serveur : un compte créé
 * sans invitation reste un compte fantôme que personne ne peut activer.
 */
export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  sendInvitation?: boolean;
}

/**
 * Opérations de masse (EF-USER-004) — `POST /api/v1/users/bulk`,
 * `BulkUserWeb.BulkRequest` / `BulkResult` / `BulkOutcome`
 * (`UserAccountController`, rôles `ADMIN` / `SUPER_ADMIN` /
 * `SCHOOL_ADMINISTRATION`).
 *
 * <p>`confirm` absent ou `false` → **aperçu seul, aucune écriture**
 * (RG-034) : le serveur calcule exactement le même résultat qu'à
 * l'exécution (éligibles / ignorés / refusés), sans rien modifier.
 * `confirm: true` exécute réellement l'action, compte par compte,
 * isolant les échecs individuels sans faire échouer le lot entier.
 */
export const BULK_ACTIONS = ['SUSPEND', 'RESTORE', 'ARCHIVE', 'RESEND_INVITATION'] as const;
export type BulkAction = (typeof BULK_ACTIONS)[number];

export const BULK_ACTION_LABELS: Record<BulkAction, string> = {
  SUSPEND: 'Suspendre',
  RESTORE: 'Réactiver',
  ARCHIVE: 'Archiver',
  RESEND_INVITATION: "Réémettre l'invitation",
};

export function bulkActionLabel(action: string): string {
  return (BULK_ACTION_LABELS as Record<string, string>)[action] ?? action;
}

/** Maximum d'identifiants acceptés par lot (`BulkRequest.userIds`, `@Size(max = 500)`). */
export const BULK_MAX_USER_IDS = 500;

export interface BulkRequest {
  action: BulkAction;
  userIds: string[];
  reason: string;
  /** Absent/`false` = aperçu ; `true` = exécution réelle. */
  confirm?: boolean;
}

export const BULK_OUTCOMES = ['ELIGIBLE', 'IGNORED', 'REJECTED'] as const;
export type BulkOutcomeStatus = (typeof BULK_OUTCOMES)[number];

export const BULK_OUTCOME_LABELS: Record<BulkOutcomeStatus, string> = {
  ELIGIBLE: 'Éligible',
  IGNORED: 'Ignoré',
  REJECTED: 'Refusé',
};

export function bulkOutcomeLabel(outcome: string): string {
  return (BULK_OUTCOME_LABELS as Record<string, string>)[outcome] ?? outcome;
}

/** Un compte du lot et l'issue qui lui a été appliquée (ou le serait). */
export interface BulkOutcome {
  userId: string;
  /** `null` si l'identifiant ne correspondait à aucun compte. */
  email: string | null;
  outcome: BulkOutcomeStatus;
  /** Motif lisible (déjà ÉLIGIBLE, rôle protégé, identifiant inconnu…). */
  reason: string;
}

export interface BulkResult {
  /** `false` pour un aperçu — rien n'a été écrit. */
  applied: boolean;
  action: BulkAction;
  requested: number;
  eligible: number;
  ignored: number;
  rejected: number;
  outcomes: BulkOutcome[];
}

/**
 * Détection de doublons (EF-USER-005) — `GET /api/v1/users/duplicates`,
 * `BulkUserWeb.DuplicateGroup` / `DuplicateCandidate`
 * (`UserAccountController`, rôles `ADMIN` / `SUPER_ADMIN` uniquement —
 * plus restreint que les opérations de masse).
 *
 * <p>Le service **signale** les doublons, il ne les fusionne ni ne les
 * supprime jamais (docs/02 §9.5) : aucune route de fusion n'existe côté
 * serveur, et cette interface n'en propose donc aucune. La réponse est un
 * simple tableau JSON, sans pagination — recalculé à chaque appel, jamais
 * mis en cache côté serveur.
 */
export interface DuplicateCandidate {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  status: AccountStatus;
  createdAt: string;
}

export interface DuplicateGroup {
  /** Valeur normalisée à l'origine du rapprochement (nom ou téléphone). */
  signature: string;
  /** Motif lisible, ex. « Même nom et prénom, à la casse et aux accents près. » */
  reason: string;
  accounts: DuplicateCandidate[];
}

/**
 * Comparaison contrôlée de deux doublons (ANO-USER-001) —
 * `POST /api/v1/users/duplicates/compare`,
 * `DuplicateComparisonWeb.CompareRequest` / `ComparisonResponse`
 * (`UserAccountController`, rôles `ADMIN` / `SUPER_ADMIN` — même
 * périmètre que `GET .../duplicates`).
 *
 * <p><strong>Lecture seule.</strong> L'endpoint ne fusionne rien, ne
 * modifie rien, n'écrit aucune trace : il renvoie une *simulation*
 * (concordances, divergences, conflits bloquants, volume de données
 * rattaché, verdict informatif). Aucune route de fusion n'existe côté
 * serveur ; cette interface n'expose donc aucun bouton « Fusionner »
 * actif (docs/02 §9.5).
 */
export interface DuplicateCompareRequest {
  firstUserId: string;
  secondUserId: string;
}

/** `DuplicateComparisonWeb.Assessment` — verdict *informatif*, jamais une action. */
export type DuplicateAssessment =
  | 'POTENTIALLY_SAFE'
  | 'MANUAL_REVIEW_REQUIRED'
  | 'NOT_MERGEABLE';

export const DUPLICATE_ASSESSMENT_LABELS: Record<DuplicateAssessment, string> = {
  POTENTIALLY_SAFE: 'Fusion potentiellement sûre',
  MANUAL_REVIEW_REQUIRED: 'Revue manuelle requise',
  NOT_MERGEABLE: 'Fusion impossible',
};

export function duplicateAssessmentLabel(value: string): string {
  return (DUPLICATE_ASSESSMENT_LABELS as Record<string, string>)[value] ?? value;
}

/** Un côté de la comparaison — `DuplicateComparisonWeb.ComparisonSide`. */
export interface DuplicateComparisonSide {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  status: AccountStatus;
  roles: string[];
  /** Refonte 2026-09 : le rôle STUDENT est l'unique source de vérité du statut apprenant. */
  isStudent: boolean;
  studentNumber: string | null;
  hasActiveEnrollment: boolean;
  hasLoginCredential: boolean;
  mfaConfigured: boolean;
  passkeys: number;
  trustedDevices: number;
  createdAt: string;
  lastLoginAt: string | null;
}

/** Un point de divergence, de blocage ou de vigilance — `DuplicateComparisonWeb.Note`. */
export interface DuplicateComparisonNote {
  code: string;
  detail: string;
}

export interface DuplicateComparisonResponse {
  first: DuplicateComparisonSide;
  second: DuplicateComparisonSide;
  /** Champs concordants (clés machine : `normalizedName`, `phone`, `status`…). */
  matchingFields: string[];
  /** Champs divergents (`name`, `email`, `roles`, `activeEnrollment`…). */
  differentFields: string[];
  /** Conflits bloquants : au moins un ⇒ `assessment = NOT_MERGEABLE`. */
  conflicts: DuplicateComparisonNote[];
  /** Points de vigilance non bloquants. */
  warnings: DuplicateComparisonNote[];
  /** Conséquences qu'une fusion — non réalisée ici — devrait traiter. */
  consequences: string[];
  /** Décompte, par catégorie, des données rattachées aux deux comptes. */
  dependencySummary: Record<string, number>;
  assessment: DuplicateAssessment;
  reasons: string[];
}

/** Libellés lisibles des clés de champ renvoyées par la comparaison. */
export const DUPLICATE_FIELD_LABELS: Record<string, string> = {
  email: 'Adresse électronique',
  normalizedName: 'Nom normalisé',
  name: 'Nom et prénom',
  phone: 'Téléphone',
  status: 'Statut',
  roles: 'Rôles',
  studentNumber: 'Numéro étudiant',
  activeEnrollment: 'Inscription active',
};

export function duplicateFieldLabel(key: string): string {
  return DUPLICATE_FIELD_LABELS[key] ?? key;
}

/** Libellés lisibles des clés du résumé des dépendances. */
export const DUPLICATE_DEPENDENCY_LABELS: Record<string, string> = {
  enrollments: 'Inscriptions',
  activeEnrollments: 'Inscriptions actives',
  attendanceRecords: 'Présences enregistrées',
  justifications: 'Justificatifs déposés',
  earlyDepartures: 'Départs anticipés',
  claims: 'Réclamations',
  notifications: 'Notifications',
  pushSubscriptions: 'Abonnements push',
  invitations: 'Invitations',
  passkeys: 'Passkeys',
  trustedDevices: 'Appareils de confiance',
  activeRoles: 'Rôles actifs',
};

export function duplicateDependencyLabel(key: string): string {
  return DUPLICATE_DEPENDENCY_LABELS[key] ?? key;
}
