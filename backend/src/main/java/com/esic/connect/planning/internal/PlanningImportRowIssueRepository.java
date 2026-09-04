package com.esic.connect.planning.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface PlanningImportRowIssueRepository extends JpaRepository<PlanningImportRowIssue, Long> {

    List<PlanningImportRowIssue> findByRow_IdInOrderByIdAsc(Collection<Long> rowIds);

    /**
     * Supprime les anomalies d'une ligne avant de rejouer son analyse
     * (EF-PLAN-003). Les conserver ferait apparaître une ligne corrigée
     * comme toujours fautive.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "delete from PlanningImportRowIssue issue where issue.row.id = :rowId")
    void deleteByRowId(@org.springframework.data.repository.query.Param("rowId") Long rowId);
}
