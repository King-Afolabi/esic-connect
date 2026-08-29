/**
 * Types de la consultation des **référentiels académiques**, strictement
 * alignés sur le contrat du module back-end `academic`
 * (`com.esic.connect.academic.internal`) :
 *
 * - `GET /api/v1/academic-years`            → `PageResponse<AcademicYearResponse>`
 * - `GET /api/v1/academic-years/{publicId}` → `AcademicYearResponse`
 * - `GET /api/v1/programs`                  → `PageResponse<ProgramResponse>`
 * - `GET /api/v1/programs/{publicId}`       → `ProgramResponse`
 * - `GET /api/v1/programs/{id}/levels`      → `PageResponse<ProgramLevelResponse>`
 * - `GET /api/v1/program-levels/{publicId}` → `ProgramLevelResponse`
 * - `GET /api/v1/promotions`               → `PageResponse<PromotionResponse>`
 * - `GET /api/v1/promotions/{publicId}`    → `PromotionResponse`
 * - `GET /api/v1/class-groups`             → `PageResponse<ClassGroupResponse>`
 * - `GET /api/v1/class-groups/{publicId}`  → `ClassGroupResponse`
 *
 * Chaque propriété correspond à un composant du `record` Java associé ;
 * aucun champ n'est inventé. Ces routes de lecture sont réservées côté
 * serveur à `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` /
 * `PEDAGOGICAL_MANAGER` (`AcademicWeb.READ_ROLES`). Pour un
 * `PEDAGOGICAL_MANAGER`, le service filtre en plus par périmètre
 * (`AcademicScopeGuard`) : une consultation hors périmètre renvoie
 * `403 ACAD_FORBIDDEN`.
 */

/** `AcademicStatus` (docs/04 §5.1) — archivage logique, aucune suppression. */
export const ACADEMIC_STATUSES = ['ACTIVE', 'ARCHIVED'] as const;
export type AcademicStatus = (typeof ACADEMIC_STATUSES)[number];

export const ACADEMIC_STATUS_LABELS: Record<AcademicStatus, string> = {
  ACTIVE: 'Actif',
  ARCHIVED: 'Archivé',
};

export function academicStatusLabel(status: string): string {
  return (ACADEMIC_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** `ProgramType` (docs/04 §12.2). */
export const PROGRAM_TYPES = ['BTS', 'BACHELOR', 'MASTER', 'OTHER'] as const;
export type ProgramType = (typeof PROGRAM_TYPES)[number];

export const PROGRAM_TYPE_LABELS: Record<ProgramType, string> = {
  BTS: 'BTS',
  BACHELOR: 'Bachelor',
  MASTER: 'Mastère',
  OTHER: 'Autre',
};

export function programTypeLabel(type: string): string {
  return (PROGRAM_TYPE_LABELS as Record<string, string>)[type] ?? type;
}

/** Enveloppe de pagination stable (`PageResponse<T>` côté serveur). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Vue API d'une année scolaire — `AcademicYearResponse`. */
export interface AcademicYearResponse {
  publicId: string;
  code: string;
  name: string;
  /** `LocalDate` (`yyyy-MM-dd`). */
  startDate: string;
  endDate: string;
  status: AcademicStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une formation — `ProgramResponse`. */
export interface ProgramResponse {
  publicId: string;
  code: string;
  name: string;
  programType: ProgramType;
  description: string | null;
  status: AcademicStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'un niveau — `ProgramLevelResponse`. */
export interface ProgramLevelResponse {
  publicId: string;
  programPublicId: string;
  code: string;
  name: string;
  sequenceNumber: number;
  status: AcademicStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une promotion — `PromotionResponse`. */
export interface PromotionResponse {
  publicId: string;
  programPublicId: string;
  academicYearPublicId: string;
  code: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: AcademicStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une classe / groupe — `ClassGroupResponse`. */
export interface ClassGroupResponse {
  publicId: string;
  promotionPublicId: string;
  programLevelPublicId: string;
  /** `public_id` d'un site (référentiel `organization`) ou `null`. */
  sitePublicId: string | null;
  code: string;
  name: string;
  capacity: number | null;
  status: AcademicStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Toute entité du référentiel académique renvoyée par l'API. */
export type AcademicRecord =
  | AcademicYearResponse
  | ProgramResponse
  | ProgramLevelResponse
  | PromotionResponse
  | ClassGroupResponse;

export type AcademicResourceSlug =
  | 'academic-years'
  | 'programs'
  | 'program-levels'
  | 'promotions'
  | 'class-groups';

export type SortDirection = 'asc' | 'desc';

/** Paramètres communs de consultation d'une liste académique. */
export interface AcademicListQuery {
  /** `q` — sous-chaîne de **code ou nom** (normalisée côté serveur). */
  q?: string | null;
  status?: AcademicStatus | null;
  /** `sort` — `champ` ou `champ,asc|desc`, champ dans la liste blanche du service. */
  sort?: string | null;
  page?: number;
  size?: number;
}

/** `GET /api/v1/promotions` — filtres supplémentaires réellement exposés. */
export interface PromotionListQuery extends AcademicListQuery {
  /** `program` — `public_id` d'une formation. */
  program?: string | null;
  /** `academicYear` — `public_id` d'une année scolaire. */
  academicYear?: string | null;
}

/** `GET /api/v1/class-groups` — filtres supplémentaires réellement exposés. */
export interface ClassGroupListQuery extends AcademicListQuery {
  /** `promotion` — `public_id` d'une promotion. */
  promotion?: string | null;
  /** `programLevel` — `public_id` d'un niveau. */
  programLevel?: string | null;
  /** `site` — `public_id` d'un site. */
  site?: string | null;
}

/**
 * Formate une date ISO (`yyyy-MM-dd` ou `Instant`) en `jj/mm/aaaa`, en
 * UTC pour rester déterministe quel que soit le fuseau. Renvoie `—` pour
 * une valeur absente ou illisible.
 */
export function formatIsoDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const day = String(date.getUTCDate()).padStart(2, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getUTCFullYear()}`;
}

/** `début – fin` ; `fin` absente → `… en cours` ; les deux absentes → `—`. */
export function formatPeriod(
  start: string | null | undefined,
  end: string | null | undefined,
): string {
  if (!start && !end) {
    return '—';
  }
  return `${formatIsoDate(start)} – ${end ? formatIsoDate(end) : 'en cours'}`;
}
