package com.esic.connect.coursesession.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AttendanceCheckpointRepository extends JpaRepository<AttendanceCheckpoint, Long> {

    /** Points de contrôle d'une séance, triés pour l'affichage (V10). */
    List<AttendanceCheckpoint> findByCourseSessionIdOrderByDisplayOrderAscIdAsc(Long courseSessionId);

    /**
     * @deprecated compat V9 (point de contrôle unique). Utiliser
     * {@link #findByCourseSessionIdOrderByDisplayOrderAscIdAsc} ou
     * {@link #findFirstByCourseSessionIdOrderByDisplayOrderAscIdAsc}.
     */
    @Deprecated
    Optional<AttendanceCheckpoint> findByCourseSessionId(Long courseSessionId);

    Optional<AttendanceCheckpoint> findByPublicId(UUID publicId);

    Optional<AttendanceCheckpoint> findByCourseSessionIdAndPublicId(Long courseSessionId, UUID publicId);

    boolean existsByCourseSessionIdAndDisplayOrder(Long courseSessionId, int displayOrder);

    /**
     * Point de contrôle {@code START} d'une séance — celui créé
     * automatiquement à la création (compat V9 : ancien point de contrôle
     * unique). Renvoie le premier par ordre d'affichage si plusieurs
     * {@code START} existent (ne devrait pas arriver : garde applicative).
     */
    Optional<AttendanceCheckpoint> findFirstByCourseSessionIdOrderByDisplayOrderAscIdAsc(Long courseSessionId);
}
