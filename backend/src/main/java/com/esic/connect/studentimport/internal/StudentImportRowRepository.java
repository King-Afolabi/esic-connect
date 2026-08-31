package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentImportRowRepository
        extends JpaRepository<StudentImportRow, Long>, JpaSpecificationExecutor<StudentImportRow> {

    Optional<StudentImportRow> findByPublicId(UUID publicId);

    List<StudentImportRow> findByJobIdOrderByRowNumberAsc(Long jobId);

    long countByJobId(Long jobId);
}
