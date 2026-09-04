package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StudentImportRowIssueRepository extends JpaRepository<StudentImportRowIssue, Long> {

    long countByRowId(Long rowId);

    List<StudentImportRowIssue> findByRow_IdInOrderByIdAsc(Collection<Long> rowIds);

    /**
     * Supprime les anomalies d'une ligne avant de rejouer sa validation
     * (EF-IMP-006). Les conserver ferait apparaître une ligne corrigée
     * comme toujours fautive.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "delete from StudentImportRowIssue issue where issue.row.id = :rowId")
    void deleteByRowId(@org.springframework.data.repository.query.Param("rowId") Long rowId);
}
