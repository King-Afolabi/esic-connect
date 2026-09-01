package com.esic.connect.planning.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PlanningImportJobIssueRepository extends JpaRepository<PlanningImportJobIssue, Long> {

    List<PlanningImportJobIssue> findByJob_IdOrderByIdAsc(Long jobId);

    void deleteByJob_Id(Long jobId);
}
