package com.esic.connect.coursesession.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TeacherSubstitutionRepository extends JpaRepository<TeacherSubstitution, Long> {

    Optional<TeacherSubstitution> findByPublicId(UUID publicId);

    List<TeacherSubstitution> findByCourseSessionIdOrderByValidFromAscIdAsc(Long courseSessionId);

    List<TeacherSubstitution> findByCourseSessionIdAndStatus(Long courseSessionId,
                                                            TeacherSubstitutionStatus status);

    /**
     * Substitutions {@code ACTIVE} d'une séance, verrouillées en écriture —
     * pour sérialiser la création concurrente (contrôle applicatif « au
     * plus une {@code ACTIVE} applicable à un instant » : MySQL n'a pas
     * d'index partiel).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TeacherSubstitution s "
            + "where s.courseSessionId = :courseSessionId and s.status = com.esic.connect.coursesession"
            + ".internal.TeacherSubstitutionStatus.ACTIVE")
    List<TeacherSubstitution> lockActiveForSession(Long courseSessionId);

    /** Substitutions où {@code userId} est le remplaçant (tous statuts). */
    List<TeacherSubstitution> findBySubstituteTeacherUserId(Long userId);
}
