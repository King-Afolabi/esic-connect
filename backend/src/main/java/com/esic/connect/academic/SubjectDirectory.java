package com.esic.connect.academic;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public minimal des matières (EF-ACA-006).
 *
 * <p>Permet à un autre module — {@code enrollment}, pour rattacher un
 * groupe temporaire à une matière ; {@code planning} et
 * {@code coursesession} plus tard — de résoudre une matière sans dépendre
 * des classes internes d'{@code academic}. Ne renvoie ni l'entité
 * {@code Subject}, ni un repository : uniquement {@link SubjectRef},
 * composé de types standard. Même approche que
 * {@link ClassGroupDirectory}.
 */
public interface SubjectDirectory {

    /**
     * @param subjectPublicId identifiant public de la matière ; peut être {@code null}
     * @return la référence si une matière correspond, {@link Optional#empty()} sinon
     */
    Optional<SubjectRef> findByPublicId(UUID subjectPublicId);

    /**
     * @param subjectInternalId identifiant interne de la matière
     * @return la référence si une matière correspond, {@link Optional#empty()} sinon
     */
    Optional<SubjectRef> findByInternalId(long subjectInternalId);

    /**
     * Référence technique d'une matière.
     *
     * @param usable {@code true} si la matière est {@code ACTIVE} — une
     *               matière archivée ne peut plus être rattachée à
     *               quoi que ce soit de nouveau
     */
    record SubjectRef(long internalId, UUID publicId, String code, String name, boolean usable) {
    }
}
