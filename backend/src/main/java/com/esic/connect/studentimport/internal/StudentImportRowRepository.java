package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentImportRowRepository
        extends JpaRepository<StudentImportRow, Long>, JpaSpecificationExecutor<StudentImportRow> {

    Optional<StudentImportRow> findByPublicId(UUID publicId);

    List<StudentImportRow> findByJobIdOrderByRowNumberAsc(Long jobId);

    long countByJobId(Long jobId);

    /**
     * Purge des lignes filles d'un job {@code APPLIED} (rapport §12.C) —
     * la FK {@code fk_student_import_row_issue_row ON DELETE CASCADE}
     * supprime les {@code student_import_row_issue} associées. Les
     * agrégats du job (en-tête) sont conservés.
     */
    @Modifying
    void deleteByJob_Id(Long jobId);
}
