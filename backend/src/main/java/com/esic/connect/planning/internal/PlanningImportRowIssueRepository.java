package com.esic.connect.planning.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface PlanningImportRowIssueRepository extends JpaRepository<PlanningImportRowIssue, Long> {

    List<PlanningImportRowIssue> findByRow_IdInOrderByIdAsc(Collection<Long> rowIds);
}
