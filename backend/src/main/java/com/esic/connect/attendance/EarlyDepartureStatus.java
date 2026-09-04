package com.esic.connect.attendance;

/**
 * Cycle de vie d'un dossier de départ anticipé (EF-ATT-013 ; docs/02
 * §16.13). Le cahier décrit quatre gestes du formateur : accepter,
 * refuser, recommander favorablement, transmettre au responsable
 * pédagogique.
 *
 * <p>« Recommander favorablement » et « transmettre » aboutissent au même
 * état — le dossier quitte le formateur sans être tranché — et se
 * distinguent par l'<em>avis</em> joint, non par le statut. Les
 * confondre dans deux statuts distincts obligerait à décider deux fois la
 * même chose.
 *
 * <ul>
 *   <li>{@link #REQUESTED} : signalé par l'apprenant, pas encore examiné ;</li>
 *   <li>{@link #FORWARDED} : transmis au responsable pédagogique, avec ou
 *       sans avis favorable du formateur ;</li>
 *   <li>{@link #ACCEPTED} / {@link #REFUSED} : tranché, avec auteur, date
 *       et commentaire.</li>
 * </ul>
 */
public enum EarlyDepartureStatus {
    REQUESTED,
    FORWARDED,
    ACCEPTED,
    REFUSED;

    /** {@code true} si le dossier est tranché — aucune décision ultérieure. */
    public boolean isDecided() {
        return this == ACCEPTED || this == REFUSED;
    }

    /** Effet du dossier sur la journée (docs/02 §16.13). */
    public EarlyDepartureEffect effect() {
        return switch (this) {
            case ACCEPTED -> EarlyDepartureEffect.EXCUSED_PARTIAL;
            case REFUSED -> EarlyDepartureEffect.PARTIAL;
            case REQUESTED, FORWARDED -> EarlyDepartureEffect.TO_CONFIRM;
        };
    }
}
