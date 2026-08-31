package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StudentImportJobIssueRepository extends JpaRepository<StudentImportJobIssue, Long> {

    long countByJobId(Long jobId);

    List<StudentImportJobIssue> findByJobIdOrderByIdAsc(Long jobId);
}
