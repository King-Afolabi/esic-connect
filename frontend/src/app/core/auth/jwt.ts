import { isRole, Role } from '../models/role';

/**
 * Décodage NON vérifié du payload d'un JWT.
 *
 * ⚠️ Aucune signature n'est contrôlée ici. Le résultat sert uniquement à
 * l'affichage et au filtrage de la navigation. Toute autorisation réelle
 * est décidée par le back-end (docs/07-securite-rgpd.md §7 ; consigne du
 * lot : « ne pas décoder un JWT et traiter son contenu comme une
 * autorisation de confiance »).
 *
 * @returns le payload en objet, ou `null` si le jeton est illisible.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const payloadSegment = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = payloadSegment.padEnd(
      payloadSegment.length + ((4 - (payloadSegment.length % 4)) % 4),
      '=',
    );
    const json = atob(padded);
    const parsed: unknown = JSON.parse(json);
    return typeof parsed === 'object' && parsed !== null
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/** Extrait le sujet (`sub`) — identifiant public du compte. */
export function readSubject(token: string): string | null {
  const payload = decodeJwtPayload(token);
  return payload && typeof payload['sub'] === 'string' ? payload['sub'] : null;
}

/** Extrait les rôles connus depuis le claim `roles`. Valeurs inconnues ignorées. */
export function readRoles(token: string): Role[] {
  const payload = decodeJwtPayload(token);
  const raw = payload?.['roles'];
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.filter(isRole);
}

/**
 * Expiration absolue en millisecondes depuis le claim `exp` (secondes).
 * Retombe sur `now + fallbackSeconds` si le claim est absent.
 */
export function readExpiry(token: string, fallbackSeconds: number, now: number = Date.now()): number {
  const payload = decodeJwtPayload(token);
  const exp = payload?.['exp'];
  if (typeof exp === 'number' && Number.isFinite(exp)) {
    return exp * 1000;
  }
  return now + fallbackSeconds * 1000;
}
