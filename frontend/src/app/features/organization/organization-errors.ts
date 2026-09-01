import {
  NormalizedError,
  SAFE_FALLBACK_MESSAGE,
  normalizeHttpError,
} from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `organization`
 * (`OrganizationExceptionHandler`) en éléments d'affichage.
 *
 * On s'appuie sur {@link normalizeHttpError} : les messages du back-end
 * sont déjà des phrases françaises sûres, sans donnée personnelle ni
 * chemin physique. Aucune règle métier n'est réinventée.
 *
 * Seuls les codes de la {@link KNOWN_CODES liste blanche} sont considérés
 * comme connus : leur message serveur (déjà contrôlé) est conservé. Tout
 * autre code — futur ou inattendu — ainsi que tout `5xx` retombe sur un
 * message générique, avec `code` et `field` à `null`.
 *
 * Codes réellement produits par le back-end :
 * `SITE_NOT_FOUND` (404), `BUILDING_NOT_FOUND` (404), `ROOM_NOT_FOUND` (404),
 * `NETWORK_RANGE_NOT_FOUND` (404), `ORG_DUPLICATE_CODE` (409),
 * `ORG_DUPLICATE_ACTIVE_RANGE` (409), `ORG_ENTITY_ARCHIVED` (409),
 * `ORG_INVALID_STATE` (409), `ORG_HAS_ACTIVE_CHILDREN` (409),
 * `ORG_ARCHIVED_PARENT` (409), `ORG_INVALID_TIME_ZONE` (400),
 * `ORG_INVALID_COUNTRY_CODE` (400), `ORG_INVALID_CIDR` (400),
 * `ORG_BUILDING_SITE_MISMATCH` (400), `ORG_INVALID_SORT` (400),
 * `ORG_INVALID_FILTER` (400).
 */
export interface OrganizationErrorView {
  status: number;
  /** Code métier quand il est connu, sinon `null`. */
  code: string | null;
  /** Message présentable à l'utilisateur (jamais une trace serveur). */
  message: string;
  /** Champ de formulaire à marquer en erreur, ou `null` pour un message global. */
  field: 'code' | 'timeZoneId' | 'countryCode' | 'cidr' | 'buildingPublicId' | null;
  /** Vrai pour un `404` connu (ressource introuvable). */
  notFound: boolean;
  /** Vrai pour un `403` (accès refusé). */
  forbidden: boolean;
}

const KNOWN_CODES: ReadonlySet<string> = new Set([
  'SITE_NOT_FOUND',
  'BUILDING_NOT_FOUND',
  'ROOM_NOT_FOUND',
  'NETWORK_RANGE_NOT_FOUND',
  'ORG_DUPLICATE_CODE',
  'ORG_DUPLICATE_ACTIVE_RANGE',
  'ORG_ENTITY_ARCHIVED',
  'ORG_INVALID_STATE',
  'ORG_HAS_ACTIVE_CHILDREN',
  'ORG_ARCHIVED_PARENT',
  'ORG_INVALID_TIME_ZONE',
  'ORG_INVALID_COUNTRY_CODE',
  'ORG_INVALID_CIDR',
  'ORG_BUILDING_SITE_MISMATCH',
  'ORG_INVALID_SORT',
  'ORG_INVALID_FILTER',
]);

/** Codes rattachables à un champ de formulaire précis. */
const FIELD_CODES: Record<string, OrganizationErrorView['field']> = {
  ORG_DUPLICATE_CODE: 'code',
  ORG_INVALID_TIME_ZONE: 'timeZoneId',
  ORG_INVALID_COUNTRY_CODE: 'countryCode',
  ORG_INVALID_CIDR: 'cidr',
  ORG_BUILDING_SITE_MISMATCH: 'buildingPublicId',
};

const NOT_FOUND_CODES: ReadonlySet<string> = new Set([
  'SITE_NOT_FOUND',
  'BUILDING_NOT_FOUND',
  'ROOM_NOT_FOUND',
  'NETWORK_RANGE_NOT_FOUND',
]);

export function toOrganizationError(error: unknown): OrganizationErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  // Un `5xx` reste masqué même s'il porte par hasard un code connu.
  const known = normalized.status < 500 && KNOWN_CODES.has(normalized.code);
  return {
    status: normalized.status,
    code: known ? normalized.code : null,
    message: known ? normalized.message : SAFE_FALLBACK_MESSAGE,
    field: known ? (FIELD_CODES[normalized.code] ?? null) : null,
    notFound: known && normalized.status === 404 && NOT_FOUND_CODES.has(normalized.code),
    forbidden: normalized.status === 403,
  };
}
