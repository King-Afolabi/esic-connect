package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudentImportJobIssueRepository extends JpaRepository<StudentImportJobIssue, Long> {

    long countByJobId(Long jobId);
}
