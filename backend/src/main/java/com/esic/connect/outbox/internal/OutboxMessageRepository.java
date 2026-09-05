package com.esic.connect.outbox.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    boolean existsByDedupKey(String dedupKey);

    Optional<OutboxMessage> findByPublicId(UUID publicId);

    Page<OutboxMessage> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    Page<OutboxMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(OutboxStatus status);

    /**
     * Réclame le prochain lot traitable.
     *
     * <p><strong>{@code SKIP LOCKED}</strong> : plusieurs diffuseurs — le
     * drain immédiat d'une requête HTTP et le passage planifié, ou deux
     * instances de l'application — peuvent tourner en même temps. Sans
     * cette clause, le second attendrait le verrou du premier puis
     * traiterait les mêmes lignes ; avec elle, il saute simplement ce que
     * l'autre a déjà pris. C'est ce qui rend le diffuseur sûr sans
     * verrou applicatif global.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from OutboxMessage m
            where m.status in (com.esic.connect.outbox.internal.OutboxStatus.PENDING,
                               com.esic.connect.outbox.internal.OutboxStatus.FAILED)
              and m.nextAttemptAt <= :now
            order by m.id asc
            """)
    List<OutboxMessage> claimBatch(@Param("now") Instant now, Pageable pageable);
}
