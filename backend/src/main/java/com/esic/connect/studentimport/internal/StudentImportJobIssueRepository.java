package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

interface StudentImportJobIssueRepository extends JpaRepository<StudentImportJobIssue, Long> {

    long countByJobId(Long jobId);

    List<StudentImportJobIssue> findByJobIdOrderByIdAsc(Long jobId);

    /** Purge des anomalies globales d'un job {@code APPLIED} (rapport §12.C). */
    @Modifying
    void deleteByJobId(Long jobId);
}
