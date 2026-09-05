package com.esic.connect.audit.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>,
        JpaSpecificationExecutor<AuditEvent> {

    /**
     * Idempotence du gestionnaire d'outbox (V32) : une trace déjà écrite
     * par une tentative précédente ne doit pas être réécrite.
     */
    boolean existsByOutboxKey(String outboxKey);

    // Valeurs distinctes servant à peupler les filtres de l'écran de
    // consultation (EF-AUD-002). Les listes sont naturellement courtes :
    // ce sont des vocabulaires fermés du code, pas des données saisies.

    @Query("select distinct e.action from AuditEvent e order by e.action")
    java.util.List<String> distinctActions();

    @Query("select distinct e.category from AuditEvent e order by e.category")
    java.util.List<String> distinctCategories();

    @Query("select distinct e.resourceType from AuditEvent e order by e.resourceType")
    java.util.List<String> distinctResourceTypes();

    @Query("select distinct e.result from AuditEvent e order by e.result")
    java.util.List<String> distinctResults();
}
