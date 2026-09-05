package com.esic.connect.audit.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Idempotence du gestionnaire d'outbox (V32) : une trace déjà écrite
     * par une tentative précédente ne doit pas être réécrite.
     */
    boolean existsByOutboxKey(String outboxKey);
}
