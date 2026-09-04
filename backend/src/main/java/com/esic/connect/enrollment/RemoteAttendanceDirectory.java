package com.esic.connect.enrollment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port public du suivi à distance individuel (EF-ENR-004 ; docs/02
 * §15.3).
 *
 * <p>Consommé par {@code attendance} : « sans autorisation, le canal
 * distant est refusé ». Le module appelant ne connaît ni l'entité, ni le
 * repository, ni le statut interne — seulement la réponse à la question
 * qu'il pose réellement : <em>cet apprenant est-il autorisé à suivre à
 * distance ce jour-là, dans cette classe ?</em>
 */
public interface RemoteAttendanceDirectory {

    /**
     * @param studentUserPublicId compte apprenant ; peut être {@code null}
     * @param classGroupPublicId  classe de la séance ; une autorisation
     *                            générale (sans classe) couvre toutes les
     *                            classes de l'apprenant
     * @param date                jour civil de la séance
     * @return {@code true} si une autorisation active couvre ce jour
     */
    boolean isRemoteAttendanceAuthorized(UUID studentUserPublicId, UUID classGroupPublicId,
                                         LocalDate date);
}
