package com.esic.connect.academic;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public minimal du module {@code academic}.
 *
 * <p>Permet à un autre module (ici {@code enrollment}, pour rattacher une
 * inscription à une classe et à son année scolaire) de résoudre une
 * référence technique de classe/groupe sans dépendre des classes internes
 * d'{@code academic}. Ne renvoie ni l'entité {@code ClassGroup}, ni un
 * repository, ni aucun type de {@code academic.internal} : uniquement le
 * {@link ClassGroupRef} ci-dessous, composé de types standard. Même
 * approche que {@link com.esic.connect.organization.SiteDirectory} et
 * {@link com.esic.connect.identity.UserDirectory}.
 */
public interface ClassGroupDirectory {

    /**
     * @param classGroupPublicId identifiant public de la classe (forme
     *                           UUID) ; peut être {@code null}
     * @return la référence de la classe si une classe correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<ClassGroupRef> findByPublicId(UUID classGroupPublicId);

    /**
     * @param classGroupInternalId identifiant interne de la classe
     * @return la référence de la classe si une classe correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<ClassGroupRef> findByInternalId(long classGroupInternalId);

    /**
     * Résout un <strong>lot</strong> de classes par identifiants internes
     * en <strong>une</strong> requête (bloc G1-F : périmètre du
     * responsable pédagogique sans N+1). Les identifiants inconnus sont
     * ignorés ; l'ordre du résultat n'est pas garanti.
     */
    java.util.List<ClassGroupRef> findByInternalIds(java.util.Collection<Long> classGroupInternalIds);

    /**
     * Résout une classe pour l'import CSV des apprenants à partir de ses
     * <em>codes fonctionnels</em> (rapport §4.3, §5.2). Vérifie
     * successivement l'existence de la formation, de l'année scolaire, de
     * la classe, puis l'appartenance de la classe à cette formation et à
     * cette année, et enfin que toute la chaîne est active. Ne renvoie
     * jamais l'entité {@code ClassGroup} ; ne prend <strong>aucune</strong>
     * décision de sécurité (le contrôle de périmètre passe par
     * {@link AcademicScopeDirectory}).
     *
     * @param programCode      code de formation (comparé sans tenir compte de la casse)
     * @param classCode        code de classe (idem)
     * @param academicYearCode code d'année scolaire (idem)
     * @return {@link ClassGroupResolution.Found} avec la référence et
     *         l'année civile de début de l'année scolaire, ou l'un des
     *         {@link ClassGroupResolution.Miss}
     */
    ClassGroupResolution resolveForImport(String programCode, String classCode, String academicYearCode);

    /**
     * Résultat de {@link #resolveForImport}. {@code sealed} : l'appelant
     * traite exhaustivement le cas {@link Found} et chaque {@link Miss}.
     */
    sealed interface ClassGroupResolution permits ClassGroupResolution.Found, ClassGroupResolution.Miss {

        /**
         * @param ref                   référence technique de la classe résolue
         * @param academicYearStartYear année civile de début de l'année scolaire
         *                              (pour la génération d'un numéro étudiant, rapport §3.2)
         */
        record Found(ClassGroupRef ref, int academicYearStartYear) implements ClassGroupResolution {
        }

        /** Échec de résolution, exhaustif (rapport §4.3). */
        enum Miss implements ClassGroupResolution {
            PROGRAM_UNKNOWN,
            ACADEMIC_YEAR_UNKNOWN,
            CLASS_UNKNOWN,
            CLASS_NOT_IN_PROGRAM,
            CLASS_NOT_IN_YEAR,
            CHAIN_ARCHIVED
        }
    }

    /**
     * Référence technique d'une classe, strictement suffisante pour qu'un
     * autre module stocke les clés étrangères {@code class_group_id} et
     * {@code academic_year_id}, réaffiche les identifiants publics et
     * refuse une inscription sous une chaîne de rattachement archivée.
     *
     * @param internalId             clé primaire SQL de la classe
     * @param publicId               identifiant public de la classe
     * @param code                   code fonctionnel de la classe
     * @param programPublicId        identifiant public de la formation
     * @param programCode            code de la formation
     * @param academicYearInternalId clé primaire SQL de l'année scolaire
     *                               de la promotion de la classe (valeur
     *                               de {@code enrollment.academic_year_id})
     * @param academicYearPublicId   identifiant public de cette année scolaire
     * @param academicYearCode       code de cette année scolaire
     * @param openForEnrollment      {@code true} si la classe et toute sa
     *                               chaîne de rattachement (promotion,
     *                               formation, année scolaire) sont
     *                               actives — une nouvelle inscription
     *                               n'est autorisée que dans ce cas
     */
    record ClassGroupRef(
            long internalId,
            UUID publicId,
            String code,
            UUID programPublicId,
            String programCode,
            long academicYearInternalId,
            UUID academicYearPublicId,
            String academicYearCode,
            boolean openForEnrollment) {
    }
}
