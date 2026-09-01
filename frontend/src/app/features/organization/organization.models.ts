/**
 * Types du référentiel **organisationnel** (`com.esic.connect.organization`),
 * strictement alignés sur le contrat REST du module back-end
 * (`com.esic.connect.organization.internal`) :
 *
 * - `GET  /api/v1/sites`                                   → `PageResponse<SiteResponse>`
 * - `GET  /api/v1/sites/{publicId}`                        → `SiteResponse`
 * - `POST /api/v1/sites`                                   → `SiteResponse` (201)
 * - `PATCH /api/v1/sites/{publicId}`                       → `SiteResponse`
 * - `POST /api/v1/sites/{publicId}/archive`               → 204
 * - `POST /api/v1/sites/{publicId}/restore`               → 204
 * - `GET  /api/v1/sites/{sitePublicId}/buildings`         → `PageResponse<BuildingResponse>`
 * - `POST /api/v1/sites/{sitePublicId}/buildings`         → `BuildingResponse` (201)
 * - `GET  /api/v1/buildings/{publicId}` (+ `PATCH`, `/archive`, `/restore`)
 * - `GET  /api/v1/sites/{sitePublicId}/rooms`             → `PageResponse<RoomResponse>`
 * - `POST /api/v1/sites/{sitePublicId}/rooms`             → `RoomResponse` (201)
 * - `GET  /api/v1/rooms/{publicId}` (+ `PATCH`, `/archive`, `/restore`)
 * - `GET  /api/v1/sites/{sitePublicId}/network-ranges`    → `PageResponse<SiteNetworkRangeResponse>`
 * - `POST /api/v1/sites/{sitePublicId}/network-ranges`    → `SiteNetworkRangeResponse` (201)
 * - `POST /api/v1/network-ranges/{publicId}/activate` | `/deactivate` → 204
 *
 * Aucun champ inventé : chaque propriété correspond à un composant du
 * `record` Java associé. La lecture est réservée côté serveur à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` / `PEDAGOGICAL_MANAGER`
 * (`SiteController.READ_ROLES`) ; l'écriture des sites / bâtiments / salles
 * à `ADMIN` / `SUPER_ADMIN` (`SiteController.WRITE_ROLES`) ; **toutes** les
 * opérations sur les plages réseau (lecture comprise) à `SUPER_ADMIN`
 * (`SiteNetworkRangeController` — `@PreAuthorize` au niveau de la classe).
 */

/** `OrganizationStatus` (docs/04 §4.2) — archivage logique, aucune suppression. */
export const ORGANIZATION_STATUSES = ['ACTIVE', 'ARCHIVED'] as const;
export type OrganizationStatus = (typeof ORGANIZATION_STATUSES)[number];

export const ORGANIZATION_STATUS_LABELS: Record<OrganizationStatus, string> = {
  ACTIVE: 'Actif',
  ARCHIVED: 'Archivé',
};

export function organizationStatusLabel(status: string): string {
  return (ORGANIZATION_STATUS_LABELS as Record<string, string>)[status] ?? status;
}

/** Enveloppe de pagination stable (`PageResponse<T>` côté serveur). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Vue API d'un site — `SiteResponse`. */
export interface SiteResponse {
  publicId: string;
  code: string;
  name: string;
  addressLine1: string | null;
  addressLine2: string | null;
  postalCode: string | null;
  city: string | null;
  countryCode: string | null;
  timeZoneId: string;
  status: OrganizationStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'un bâtiment — `BuildingResponse`. */
export interface BuildingResponse {
  publicId: string;
  sitePublicId: string;
  code: string;
  name: string;
  status: OrganizationStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une salle — `RoomResponse`. */
export interface RoomResponse {
  publicId: string;
  sitePublicId: string;
  buildingPublicId: string | null;
  code: string;
  name: string;
  capacity: number | null;
  floorLabel: string | null;
  staticQrReference: string | null;
  status: OrganizationStatus;
  archivedAt: string | null;
  archiveReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vue API d'une plage réseau — `SiteNetworkRangeResponse` (jamais d'IP utilisateur). */
export interface SiteNetworkRangeResponse {
  publicId: string;
  sitePublicId: string;
  cidr: string;
  label: string;
  active: boolean;
  validFrom: string | null;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

export type SortDirection = 'asc' | 'desc';

/** Paramètres communs de consultation d'une liste organisationnelle. */
export interface OrganizationListQuery {
  /** `q` — sous-chaîne de **code ou nom** (normalisée côté serveur). */
  q?: string | null;
  status?: OrganizationStatus | null;
  /** `sort` — `champ` ou `champ,asc|desc`, champ dans la liste blanche du service. */
  sort?: string | null;
  page?: number;
  size?: number;
}

/** `GET /api/v1/sites/{id}/rooms` — filtre supplémentaire réellement exposé. */
export interface RoomListQuery extends OrganizationListQuery {
  /** `building` — `public_id` d'un bâtiment. */
  building?: string | null;
}

/** `GET /api/v1/sites/{id}/network-ranges` — seul filtre exposé + tri/pagination. */
export interface NetworkRangeListQuery {
  /** `active` — `true` / `false` (chaîne). */
  active?: string | null;
  sort?: string | null;
  page?: number;
  size?: number;
}

/** Corps de `POST /api/v1/sites`. */
export interface CreateSiteRequest {
  code: string;
  name: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  countryCode?: string | null;
  timeZoneId: string;
}

/** Corps de `PATCH /api/v1/sites/{id}` (le `code` n'en fait jamais partie). */
export interface UpdateSiteRequest {
  name: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  countryCode?: string | null;
  timeZoneId: string;
}

/** Corps de `POST /api/v1/sites/{id}/buildings`. */
export interface CreateBuildingRequest {
  code: string;
  name: string;
}

/** Corps de `PATCH /api/v1/buildings/{id}` (nom seul ; code immuable). */
export interface UpdateBuildingRequest {
  name: string;
}

/** Corps de `POST /api/v1/sites/{id}/rooms`. */
export interface CreateRoomRequest {
  code: string;
  name: string;
  buildingPublicId?: string | null;
  capacity?: number | null;
  floorLabel?: string | null;
  staticQrReference?: string | null;
}

/** Corps de `PATCH /api/v1/rooms/{id}` (remplace les champs modifiables ; code immuable). */
export interface UpdateRoomRequest {
  name: string;
  buildingPublicId?: string | null;
  capacity?: number | null;
  floorLabel?: string | null;
  staticQrReference?: string | null;
}

/** Corps de `POST /api/v1/sites/{id}/network-ranges` (CIDR immuable ensuite). */
export interface CreateNetworkRangeRequest {
  cidr: string;
  label: string;
}

/** Corps commun d'archivage (site / bâtiment / salle) — motif obligatoire (audit). */
export interface ArchiveRequest {
  reason: string;
}

/**
 * Formate une date ISO (`Instant` / `yyyy-MM-dd`) en `jj/mm/aaaa`, en UTC
 * pour rester déterministe quel que soit le fuseau. Renvoie `—` pour une
 * valeur absente ou illisible.
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
