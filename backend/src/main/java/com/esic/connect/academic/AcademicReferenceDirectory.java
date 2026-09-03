package com.esic.connect.academic;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public de résolution des référentiels de haut niveau — formation
 * et année scolaire.
 *
 * <p>{@link ClassGroupDirectory} résout une classe, et suffit tant qu'une
 * ressource est rattachée à une classe. Un groupe temporaire
 * (EF-ACA-007), lui, est rattaché directement à une formation et à une
 * année : il faut donc pouvoir les résoudre sans passer par une classe.
 *
 * <p>Comme les autres ports du module, il ne renvoie ni entité JPA, ni
 * repository : uniquement des enregistrements composés de types standard.
 */
public interface AcademicReferenceDirectory {

    /**
     * @param programPublicId identifiant public de la formation ; peut être {@code null}
     * @return la référence si une formation correspond, {@link Optional#empty()} sinon
     */
    Optional<ProgramRef> findProgramByPublicId(UUID programPublicId);

    /**
     * @param academicYearPublicId identifiant public de l'année ; peut être {@code null}
     * @return la référence si une année correspond, {@link Optional#empty()} sinon
     */
    Optional<AcademicYearRef> findAcademicYearByPublicId(UUID academicYearPublicId);

    /**
     * Résout une formation par son identifiant interne — le module
     * appelant stocke la clé étrangère, il doit pouvoir la réafficher
     * sous forme publique.
     */
    Optional<ProgramRef> findProgramByInternalId(long programInternalId);

    /** Idem pour l'année scolaire. */
    Optional<AcademicYearRef> findAcademicYearByInternalId(long academicYearInternalId);

    /**
     * @param usable {@code true} si la formation est {@code ACTIVE} — une
     *               formation archivée ne peut plus recevoir de nouveau
     *               rattachement
     */
    record ProgramRef(long internalId, UUID publicId, String code, String name, boolean usable) {
    }

    /** @param usable {@code true} si l'année scolaire est {@code ACTIVE} */
    record AcademicYearRef(long internalId, UUID publicId, String code, boolean usable) {
    }
}
