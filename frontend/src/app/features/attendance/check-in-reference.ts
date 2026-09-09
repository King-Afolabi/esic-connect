/**
 * Canonicalisation d'une chaîne lue par la caméra (QR) ou destinée à un
 * tag NFC de salle, vers un **type fermé**. Aucune déduction heuristique
 * fragile : trois formes reconnues, tout le reste est `UNSUPPORTED`.
 *
 * - `DYNAMIC_ATTENDANCE_TOKEN` — jeton opaque du QR **dynamique** du
 *   formateur. Il part tel quel vers `POST /api/v1/attendance/validate`
 *   dans le champ `token` ; le serveur détermine séance, point de
 *   contrôle, expiration, inscription, anti-rejeu, retard et canal.
 * - `STATIC_ROOM_REFERENCE` — référence opaque d'une **affiche / d'un tag
 *   NFC de salle**, extraite d'une **URL interne** `…/attendance?ref=<opaque>`
 *   (forme privilégiée pour les impressions et les tags NFC), ou fournie
 *   telle quelle en compat des anciennes affiches. Elle part vers
 *   `POST /api/v1/attendance/room-qr` dans le champ `roomReference` ; le
 *   serveur applique le contrôle de plage réseau et la fenêtre de séance.
 * - `UNSUPPORTED` — URL externe, QR d'une autre application, texte libre,
 *   forme inattendue.
 *
 * Le parseur **n'invente jamais** de séance, d'apprenant, de point de
 * contrôle ni de rôle : il ne produit qu'une chaîne opaque et son type.
 * Il ne conserve rien (RG-093).
 *
 * ## Ambiguïté assumée entre le jeton dynamique et la référence de salle
 *
 * Les deux sont des chaînes Base64 URL-safe de longueur voisine : nues,
 * elles sont indistinguables. Choix : une chaîne **nue** issue de la
 * caméra est traitée comme un **jeton dynamique** (le QR du formateur
 * encode une chaîne nue). Une **référence de salle** n'est reconnue comme
 * telle que si elle arrive dans une **URL interne** `…/attendance?ref=…`
 * — ce que produisent désormais l'affiche `room-qr-poster` et les tags
 * NFC. Les anciennes affiches encodant une référence nue restent
 * utilisables par la **saisie manuelle** du « code du QR de salle », pas
 * par la caméra ; un scan renverra alors un refus serveur explicite et
 * l'écran propose la saisie manuelle.
 */

/** Type fermé produit par {@link parseCheckInReference}. */
export type CheckInReference =
  | { kind: 'DYNAMIC_ATTENDANCE_TOKEN'; token: string }
  | { kind: 'STATIC_ROOM_REFERENCE'; roomReference: string }
  | { kind: 'UNSUPPORTED' };

/** Chemin interne de l'écran d'émargement (route Angular). */
export const CHECK_IN_PATH = '/attendance';
/** Nom du paramètre portant la référence opaque de salle. */
export const CHECK_IN_REF_PARAM = 'ref';

/** Bornes défensives — le serveur revalide de toute façon. */
const OPAQUE_MIN_LENGTH = 20;
const OPAQUE_MAX_LENGTH = 128;
/** Alphabet Base64 URL-safe sans remplissage (jeton serveur et référence d'affiche). */
const OPAQUE_PATTERN = /^[A-Za-z0-9_-]+$/;

export interface ParseOptions {
  /**
   * Origines considérées comme **internes** (celle de l'application, plus
   * une éventuelle origine publique configurée). Une URL dont l'origine
   * n'y figure pas est `UNSUPPORTED` : jamais ouverte, jamais suivie.
   */
  allowedOrigins: readonly string[];
  /**
   * Autorise une chaîne **nue** à être interprétée comme une référence de
   * salle (utile pour un champ de saisie manuelle « code du QR de
   * salle »). `false` par défaut — le parseur de la caméra ne doit pas
   * confondre une référence de salle nue avec un jeton dynamique.
   */
  bareStringIsRoomReference?: boolean;
}

/**
 * Analyse une chaîne scannée / saisie et renvoie son type fermé.
 *
 * @param raw contenu brut du QR (ou d'un champ de saisie)
 * @param options origines internes autorisées + politique des chaînes nues
 */
export function parseCheckInReference(raw: string, options: ParseOptions): CheckInReference {
  const value = (raw ?? '').trim();
  if (!value) {
    return { kind: 'UNSUPPORTED' };
  }

  // 1) URL : uniquement une URL INTERNE pointant vers l'écran d'émargement
  //    avec un paramètre `ref` opaque. Tout le reste est rejeté.
  if (looksLikeUrl(value)) {
    return parseUrl(value, options.allowedOrigins);
  }

  // 2) Chaîne nue : jeton dynamique par défaut ; référence de salle
  //    seulement si l'appelant l'a explicitement demandé (saisie manuelle).
  if (isOpaque(value)) {
    return options.bareStringIsRoomReference
      ? { kind: 'STATIC_ROOM_REFERENCE', roomReference: value }
      : { kind: 'DYNAMIC_ATTENDANCE_TOKEN', token: value };
  }

  return { kind: 'UNSUPPORTED' };
}

/**
 * Construit l'URL absolue d'émargement de salle à partir du
 * `checkInPath` **fourni par le back-end** (`/attendance?ref=<opaque>`)
 * et d'une origine publique. Ne construit jamais l'URL depuis une origine
 * arbitraire : `publicOrigin` doit être l'origine configurée ou celle,
 * de confiance, d'où l'application est servie.
 *
 * @returns l'URL absolue, ou `null` si `checkInPath` est absent / mal formé
 */
export function buildRoomCheckInUrl(
  checkInPath: string | null | undefined,
  publicOrigin: string,
): string | null {
  if (!checkInPath || !checkInPath.startsWith('/')) {
    return null;
  }
  const origin = publicOrigin.replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(origin)) {
    return null;
  }
  try {
    // Valide la construction ; on renvoie la forme brute concaténée pour
    // rester fidèle au chemin exact produit par le serveur.
    const parsed = new URL(origin + checkInPath);
    if (!parsed.searchParams.get(CHECK_IN_REF_PARAM)) {
      return null;
    }
    return origin + checkInPath;
  } catch {
    return null;
  }
}

function looksLikeUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function parseUrl(value: string, allowedOrigins: readonly string[]): CheckInReference {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return { kind: 'UNSUPPORTED' };
  }
  const normalizedAllowed = allowedOrigins
    .map((origin) => origin.replace(/\/+$/, '').toLowerCase())
    .filter((origin) => /^https?:\/\//.test(origin));
  if (!normalizedAllowed.includes(url.origin.toLowerCase())) {
    // URL externe : jamais suivie, jamais ouverte automatiquement.
    return { kind: 'UNSUPPORTED' };
  }
  // Chemin exact de l'écran d'émargement (tolère un `/` final).
  const path = url.pathname.replace(/\/+$/, '') || '/';
  if (path !== CHECK_IN_PATH) {
    return { kind: 'UNSUPPORTED' };
  }
  const ref = url.searchParams.get(CHECK_IN_REF_PARAM);
  if (!ref || !isOpaque(ref.trim())) {
    return { kind: 'UNSUPPORTED' };
  }
  return { kind: 'STATIC_ROOM_REFERENCE', roomReference: ref.trim() };
}

function isOpaque(value: string): boolean {
  return (
    value.length >= OPAQUE_MIN_LENGTH &&
    value.length <= OPAQUE_MAX_LENGTH &&
    OPAQUE_PATTERN.test(value)
  );
}
