package com.esic.connect.academic;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Port public de résolution <strong>inverse</strong> du périmètre
 * pédagogique : « qui répond de cette classe ? », par opposition à
 * {@link AcademicScopeDirectory}, qui répond à « que voit l'appelant ? ».
 *
 * <p>Ce port existe pour la notification (EF-NOTIF-003). Élargir
 * l'audience d'un événement au responsable pédagogique du périmètre
 * suppose de partir de la ressource — une classe — et de remonter aux
 * comptes qui en répondent. {@link AcademicScopeDirectory} ne sait pas
 * faire ce trajet : il part toujours de l'appelant authentifié, et
 * l'exécution d'un message d'outbox n'a pas d'appelant.
 *
 * <p>Ne renvoie que des identifiants de compte. Le module
 * {@code notification} n'a pas à connaître le détail d'une affectation
 * pédagogique — sa date de fin, son caractère principal ou délégué — et
 * ne doit donc pas le recevoir.
 */
public interface PedagogicalResponsibilityDirectory {

    /**
     * Identifiants publics des comptes {@code PEDAGOGICAL_MANAGER} —
     * responsable principal comme délégués — dont l'affectation est
     * effective le jour indiqué pour au moins une des classes fournies.
     *
     * @param classGroupPublicIds identifiants publics des classes ; les
     *                            inconnues et les {@code null} sont ignorés
     * @param on                  jour d'effet de l'affectation ; obligatoire
     * @return les identifiants publics de compte, sans doublon ; vide si aucun
     */
    Set<UUID> findManagersOfClasses(Collection<UUID> classGroupPublicIds, LocalDate on);
}
