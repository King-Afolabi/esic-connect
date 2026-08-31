package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface StudentImportRowIssueRepository extends JpaRepository<StudentImportRowIssue, Long> {

    long countByRowId(Long rowId);

    List<StudentImportRowIssue> findByRow_IdInOrderByIdAsc(Collection<Long> rowIds);
}
