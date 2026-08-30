package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentImportRowRepository extends JpaRepository<StudentImportRow, Long> {

    Optional<StudentImportRow> findByPublicId(UUID publicId);

    List<StudentImportRow> findByJobIdOrderByRowNumberAsc(Long jobId);

    long countByJobId(Long jobId);
}
