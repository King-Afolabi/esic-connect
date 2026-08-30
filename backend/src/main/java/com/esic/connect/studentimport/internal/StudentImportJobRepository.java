package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface StudentImportJobRepository extends JpaRepository<StudentImportJob, Long> {

    Optional<StudentImportJob> findByPublicId(UUID publicId);
}
