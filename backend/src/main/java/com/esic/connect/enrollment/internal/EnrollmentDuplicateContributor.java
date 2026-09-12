package com.esic.connect.enrollment.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remonte à {@code identity}, <strong>en lecture seule</strong>, le volume
 * de données que {@code enrollment} rattache à un compte : inscriptions,
 * inscription active (ANO-USER-001 — comparaison de doublons).
 *
 * <p>Refonte 2026-09 : le numéro étudiant et l'existence d'un profil
 * apprenant ne sont plus des attributs remontés par ce contributeur — ils
 * sont désormais portés directement par {@code user_account}, qu'
 * {@code identity} lit lui-même sans passer par ce point d'extension.
 *
 * <p>Deux décomptes bornés. Aucune écriture, aucun effet de bord : le
 * contrat de {@link DuplicateDependencyContributor} l'exige.
 */
@Component
class EnrollmentDuplicateContributor implements DuplicateDependencyContributor {

    private final EnrollmentRepository enrollmentRepository;

    EnrollmentDuplicateContributor(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(long userInternalId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("enrollments",
                enrollmentRepository.countByUserId(userInternalId));
        counts.put("activeEnrollments",
                enrollmentRepository.countByUserIdAndStatus(userInternalId, EnrollmentStatus.ACTIVE));
        return counts;
    }
}
