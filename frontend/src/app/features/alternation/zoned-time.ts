/**
 * Encodage de l'instant d'une exception individuelle.
 *
 * Le back-end attend deux informations distinctes
 * (`StudentExceptionRequests.Create`) : des **instants absolus**
 * (`startAt` / `endAt`, ISO-8601) et un **`timeZoneId` IANA** qu'il
 * utilise ensuite pour projeter l'exception sur un jour civil. Le
 * formulaire recueille une heure locale (« heure de mur ») dans un
 * fuseau choisi ; il faut donc convertir ce couple en instant absolu
 * pour la transmission.
 *
 * Cette conversion est un simple **encodage de saisie**, imposé par le
 * contrat : elle ne calcule jamais la projection civile de l'exception
 * (rôle exclusif du back-end). Le `timeZoneId` choisi est transmis
 * **tel quel**, sans conversion vers un autre fuseau. Un fuseau inconnu
 * n'est jamais remplacé silencieusement par UTC : {@link zonedWallTimeToInstant}
 * renvoie `null` et la soumission est bloquée (et le back-end renverrait
 * de toute façon `400 ALT_INVALID_TIME_ZONE`).
 *
 * Le décalage du fuseau à l'instant considéré est résolu via
 * `Intl.DateTimeFormat` (ICU complet sous Node ≥ 18 et les navigateurs
 * modernes). Aux minutes exactes d'un changement d'heure, l'heure locale
 * peut être ambiguë ou inexistante ; le résultat est alors celui de la
 * règle standard « offset avant la transition », comportement accepté et
 * habituel côté client.
 */

const DATE_TIME_PARTS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
};

/** Vrai si `timeZoneId` est un identifiant IANA reconnu par la plateforme. */
export function isSupportedTimeZone(timeZoneId: string): boolean {
  if (!timeZoneId || !timeZoneId.trim()) {
    return false;
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: timeZoneId.trim() });
    return true;
  } catch {
    return false;
  }
}

/**
 * Convertit une heure de mur (`yyyy-MM-ddTHH:mm`, sans fuseau, telle que
 * produite par `<input type="datetime-local">`) exprimée dans
 * `timeZoneId` en instant absolu ISO-8601 (`…Z`).
 *
 * @returns l'instant ISO-8601, ou `null` si l'entrée est mal formée ou si
 *          le fuseau est inconnu (jamais de repli sur UTC).
 */
export function zonedWallTimeToInstant(
  wallTime: string,
  timeZoneId: string,
): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(
    (wallTime ?? '').trim(),
  );
  if (!match || !isSupportedTimeZone(timeZoneId)) {
    return null;
  }
  const [, y, mo, d, h, mi, s] = match;
  const wallAsUtcMs = Date.UTC(
    Number(y),
    Number(mo) - 1,
    Number(d),
    Number(h),
    Number(mi),
    s ? Number(s) : 0,
  );

  // Décalage du fuseau au voisinage de cet instant : on formate
  // `wallAsUtcMs` dans le fuseau cible, on relit les composantes, et
  // l'écart avec l'heure de mur donne l'offset (méthode standard sans
  // bibliothèque de fuseaux).
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: timeZoneId.trim(),
    ...DATE_TIME_PARTS,
  });
  const parts = formatter.formatToParts(new Date(wallAsUtcMs));
  const lookup = (type: Intl.DateTimeFormatPartTypes): number => {
    const found = parts.find((p) => p.type === type);
    return found ? Number(found.value) : 0;
  };
  const zoneWallMs = Date.UTC(
    lookup('year'),
    lookup('month') - 1,
    lookup('day'),
    lookup('hour') % 24,
    lookup('minute'),
    lookup('second'),
  );
  const offsetMs = zoneWallMs - wallAsUtcMs;
  return new Date(wallAsUtcMs - offsetMs).toISOString();
}

/**
 * Fuseaux IANA proposés dans le sélecteur. Liste **volontairement
 * restreinte** aux fuseaux utiles à l'ESIC et à quelques cas courants —
 * ce n'est pas un référentiel exhaustif. La saisie reste validée par le
 * back-end (`ZoneId.of` → `400 ALT_INVALID_TIME_ZONE`).
 */
export const COMMON_TIME_ZONES: readonly string[] = [
  'Europe/Paris',
  'Europe/London',
  'Europe/Brussels',
  'Europe/Madrid',
  'Europe/Berlin',
  'Atlantic/Reunion',
  'Indian/Antananarivo',
  'America/Cayenne',
  'America/Guadeloupe',
  'America/Martinique',
  'Pacific/Noumea',
  'UTC',
];
