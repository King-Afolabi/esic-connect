package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudentImportRowIssueRepository extends JpaRepository<StudentImportRowIssue, Long> {

    long countByRowId(Long rowId);
}
