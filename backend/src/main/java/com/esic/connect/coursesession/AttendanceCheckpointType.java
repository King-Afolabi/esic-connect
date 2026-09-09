package com.esic.connect.coursesession;

/**
 * Type d'un point de contrôle d'émargement (V10, étendu par V25).
 *
 * <p>V10 généralisait le point unique de V9 en points typés
 * {@link #START} / {@link #END} / {@link #CUSTOM}, en indiquant que les
 * quatre points journaliers du cahier restaient « réalisables via des
 * points {@code CUSTOM} libellés ». C'était vrai fonctionnellement et
 * faux structurellement : <strong>un calcul journalier ne peut pas se
 * fonder sur un libellé libre</strong> (docs/02 §16.3). Les quatre types
 * sont donc devenus des valeurs à part entière (EF-ATT-003).
 *
 * <ul>
 *   <li>{@link #START} : arrivée / début de séance — créé automatiquement
 *       avec la séance, parcours d'émargement nominal ;</li>
 *   <li>{@link #END} : fin de séance / départ ;</li>
 *   <li>{@link #CUSTOM} : point de contrôle intermédiaire libre
 *       (formats atypiques, docs/02 §16.2) ;</li>
 *   <li>{@link #MORNING_ARRIVAL}, {@link #MORNING_BREAK_RETURN},
 *       {@link #AFTERNOON_ARRIVAL}, {@link #AFTERNOON_BREAK_RETURN} :
 *       les quatre points journaliers nommés, seuls à entrer dans le
 *       calcul de demi-journée et de journée (§16.3).</li>
 * </ul>
 */
public enum AttendanceCheckpointType {
    START,
    END,
    CUSTOM,
    MORNING_ARRIVAL,
    MORNING_BREAK_RETURN,
    AFTERNOON_ARRIVAL,
    AFTERNOON_BREAK_RETURN;

    /** Demi-journée du matin (docs/02 §16.3). */
    public boolean isMorning() {
        return this == MORNING_ARRIVAL || this == MORNING_BREAK_RETURN;
    }

    /** Demi-journée de l'après-midi (docs/02 §16.3). */
    public boolean isAfternoon() {
        return this == AFTERNOON_ARRIVAL || this == AFTERNOON_BREAK_RETURN;
    }

    /**
     * Point journalier nommé : entre dans le calcul de la demi-journée.
     * {@code START}, {@code END} et {@code CUSTOM} n'y entrent pas — ils
     * servent au parcours d'émargement, pas au découpage journalier.
     */
    public boolean isDaily() {
        return isMorning() || isAfternoon();
    }
}
