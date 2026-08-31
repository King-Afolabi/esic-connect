package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface StudentImportJobRepository
        extends JpaRepository<StudentImportJob, Long>, JpaSpecificationExecutor<StudentImportJob> {

    Optional<StudentImportJob> findByPublicId(UUID publicId);
}
