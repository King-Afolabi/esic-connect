package com.esic.connect.attendance;

/**
 * Résultat journalier d'assiduité d'un apprenant (docs/02 §16.3,
 * EF-ATT-004).
 *
 * <p>Le cahier fixe la table de vérité, elle est reprise telle quelle :
 *
 * <ul>
 *   <li>{@link #FULL_DAY} : les quatre points journaliers validés de façon
 *       cohérente ;</li>
 *   <li>{@link #MORNING} : les deux points du matin validés, l'après-midi
 *       n'étant pas attendu ;</li>
 *   <li>{@link #AFTERNOON} : symétrique ;</li>
 *   <li>{@link #PARTIAL} : validations incomplètes ;</li>
 *   <li>{@link #EXCUSED_PARTIAL} : journée incomplète, mais le départ
 *       anticipé a été accepté (EF-ATT-013 ; docs/02 §16.13) ;</li>
 *   <li>{@link #TO_CONFIRM} : incohérence — typiquement un retour de pause
 *       validé sans l'arrivée qui le précède ;</li>
 *   <li>{@link #ABSENT} : aucune validation, aucune correction ;</li>
 *   <li>{@link #EXCUSED} : absence justifiée et acceptée ;</li>
 *   <li>{@link #COMPANY} : journée en entreprise — jamais une absence
 *       (RG-028) ;</li>
 *   <li>{@link #NOT_EXPECTED} : aucune séance attendue ce jour-là.</li>
 * </ul>
 *
 * <p>{@link #EXCUSED_PARTIAL} vient de §16.13 et non de la table de
 * §16.3 : c'est l'effet d'un départ anticipé accepté sur une journée
 * incomplète. Sans lui, une journée écourtée avec l'accord du
 * responsable serait indistinguable d'une journée écourtée sans
 * autorisation.
 *
 * <p>{@link #COMPANY} et {@link #NOT_EXPECTED} ne figurent pas dans la
 * table du cahier : ils y sont ajoutés parce que la table suppose une
 * journée attendue. Sans eux, une journée en entreprise tomberait dans
 * {@code ABSENT}, ce que la règle RG-028 interdit explicitement.
 */
public enum DailyAttendanceResult {
    FULL_DAY,
    MORNING,
    AFTERNOON,
    PARTIAL,
    EXCUSED_PARTIAL,
    TO_CONFIRM,
    ABSENT,
    EXCUSED,
    COMPANY,
    NOT_EXPECTED
}
