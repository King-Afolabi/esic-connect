package com.esic.connect.alternation;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port public minimal du module {@code alternation}.
 *
 * <p>Permet au module {@code attendance} de connaître le contexte
 * d'alternance <em>effectif</em> d'une inscription à une date — pour ne
 * jamais compter une période en entreprise ({@code COMPANY}) comme une
 * absence scolaire (docs/02 §8.4) et pour signaler séparément les jours
 * indéterminés ({@code UNKNOWN}).
 *
 * <p>La résolution est faite <strong>sans</strong> contrôle d'accès de
 * l'appelant : le module {@code attendance} a déjà vérifié le périmètre
 * de la classe / séance concernée avant d'appeler cette méthode. Ne
 * renvoie ni entité JPA, ni type de {@code alternation.internal}.
 */
public interface AlternationDirectory {

    /**
     * @param enrollmentPublicId identifiant public de l'inscription ; peut
     *                           être {@code null}
     * @param date               jour civil de référence (date de la séance)
     * @return le contexte effectif ; {@link Axis#UNKNOWN} si l'inscription
     *         est inconnue, si aucune règle ne s'applique, ou si le
     *         contexte est contradictoire
     */
    EnrollmentContextView resolveEnrollmentContext(UUID enrollmentPublicId, LocalDate date);

    /** Axe école / entreprise d'une inscription à une date. */
    enum Axis {
        SCHOOL,
        COMPANY,
        UNKNOWN
    }

    /**
     * @param effective          contexte effectif (après exceptions individuelles)
     * @param pattern            contexte issu du seul rythme de la classe
     * @param coveredByException {@code true} si une exception individuelle
     *                           active recouvre la date
     */
    record EnrollmentContextView(Axis effective, Axis pattern, boolean coveredByException) {
    }
}
