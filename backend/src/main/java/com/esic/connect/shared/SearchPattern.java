package com.esic.connect.shared;

import java.util.Locale;

/**
 * Normalisation d'un fragment de recherche globale (EF-USER-009 ;
 * docs/02 §22.7).
 *
 * <p>Placé dans {@code shared} parce que <strong>chaque module fait sa
 * propre recherche sur sa propre donnée</strong> : sans règle commune,
 * une casse ou un caractère joker seraient traités différemment selon
 * l'entité trouvée, et le même mot ne rendrait pas les mêmes résultats
 * d'une ligne à l'autre.
 *
 * <p>Deux protections, l'une fonctionnelle et l'autre non :
 * <ul>
 *   <li>les jokers {@code %} et {@code _} saisis par l'utilisateur sont
 *       échappés — sans quoi une recherche sur {@code "%"} ramènerait
 *       tout le référentiel ;</li>
 *   <li>un fragment trop court est refusé : deux caractères suffisent à
 *       balayer une base entière, ce qui est un moyen d'énumération et
 *       non une recherche.</li>
 * </ul>
 */
public final class SearchPattern {

    /** Un fragment plus court n'est pas une recherche, c'est un balayage. */
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 80;
    private static final int MAX_RESULTS = 25;

    private SearchPattern() {
    }

    /**
     * @return le motif {@code LIKE} en minuscules, jokers utilisateur
     *         échappés ; {@code null} si le fragment est inexploitable —
     *         l'appelant renvoie alors une liste vide plutôt que tout
     */
    public static String of(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_LENGTH) {
            return null;
        }
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Borne le nombre de lignes qu'une recherche peut ramener. */
    public static int bound(int limit) {
        return Math.max(1, Math.min(limit, MAX_RESULTS));
    }
}
