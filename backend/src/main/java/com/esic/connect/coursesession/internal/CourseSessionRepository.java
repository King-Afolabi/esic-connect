package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CourseSessionRepository
        extends JpaRepository<CourseSession, Long>, JpaSpecificationExecutor<CourseSession> {

    Optional<CourseSession> findByPublicId(UUID publicId);

    /** Chargement groupé (évite le N+1 des cibles de notification G1-D). */
    List<CourseSession> findByPublicIdIn(java.util.Collection<UUID> publicIds);

    /**
     * Verrou de ligne sur la séance — sérialise les opérations
     * concurrentes qui dépendent d'un invariant « au plus un … par
     * séance » (G1-C.2 : au plus une substitution {@code ACTIVE}
     * applicable). {@code SELECT … FOR UPDATE}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSession s where s.id = :id")
    Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);

    /**
     * Séance d'origine planning liée à un créneau stable précis
     * (identité inter-versions — idempotence de la publication,
     * DEC-G1-002).
     */
    Optional<CourseSession> findByPlanningSlotPublicId(UUID planningSlotPublicId);

    /**
     * Séances d'origine planning d'une classe donnée (jointure
     * {@code session_class}) — utilisé pour la supersession des créneaux
     * retirés d'une nouvelle version.
     */
    @Query("select distinct s from CourseSession s join s.classes c "
            + "where c.classGroupId = :classGroupId and s.planningSlotPublicId is not null "
            + "and s.status = :status and s.supersededByScheduling = false")
    List<CourseSession> findPlanningSessionsForClass(@Param("classGroupId") Long classGroupId,
                                                    @Param("status") SessionLifecycle status);

    /**
     * Charge les rattachements de classes de <strong>plusieurs</strong>
     * séances en une requête (dette T-03).
     *
     * <p>{@code CourseSession.classes} est une collection {@code LAZY} :
     * la parcourir séance par séance produit une requête par séance —
     * exactement le coût proportionnel au nombre d'éléments affichés que
     * NFR-PERF-08 interdit. Le {@code join fetch} initialise la
     * collection pour tout le lot ; {@code distinct} évite les doublons
     * de racine dus au produit cartésien.
     *
     * <p>Une séance sans classe rattachée ne remonte pas ici : sa
     * collection reste non initialisée, ce qui est sans conséquence —
     * l'appelant lit alors une collection vide sans requête
     * supplémentaire (aucun rattachement à charger).
     */
    @Query("select distinct s from CourseSession s left join fetch s.classes where s.id in :ids")
    List<CourseSession> findAllWithClassesByIdIn(@Param("ids") java.util.Collection<Long> ids);
}