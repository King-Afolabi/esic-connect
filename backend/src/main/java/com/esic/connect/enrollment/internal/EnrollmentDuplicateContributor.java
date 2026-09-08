package com.esic.connect.enrollment.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remonte à {@code identity}, <strong>en lecture seule</strong>, le volume
 * de données que {@code enrollment} rattache à un compte : profil
 * apprenant, inscriptions, inscription active, numéro étudiant
 * (ANO-USER-001 — comparaison de doublons).
 *
 * <p>Trois décomptes bornés et un attribut. Aucune écriture, aucun effet
 * de bord : le contrat de {@link DuplicateDependencyContributor} l'exige.
 */
@Component
class EnrollmentDuplicateContributor implements DuplicateDependencyContributor {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;

    EnrollmentDuplicateContributor(EnrollmentRepository enrollmentRepository,
                                   StudentProfileRepository studentProfileRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(long userInternalId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("studentProfile",
                studentProfileRepository.existsByUserId(userInternalId) ? 1L : 0L);
        counts.put("enrollments",
                enrollmentRepository.countByStudentProfile_UserId(userInternalId));
        counts.put("activeEnrollments",
                enrollmentRepository.countByStudentProfile_UserIdAndStatus(
                        userInternalId, EnrollmentStatus.ACTIVE));
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> attributesFor(long userInternalId) {
        return studentProfileRepository.findByUserId(userInternalId)
                .map(profile -> Map.of("studentNumber", profile.getStudentNumber()))
                .orElseGet(Map::of);
    }
}
