package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.StudentImportDashboardDirectory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du port {@link StudentImportDashboardDirectory} (bloc
 * G1-F). Confinée à {@code studentimport.internal} ; lecture bornée.
 */
@Component
class DefaultStudentImportDashboardDirectory implements StudentImportDashboardDirectory {

    private final StudentImportJobRepository jobRepository;

    DefaultStudentImportDashboardDirectory(StudentImportJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportJobDigest> recentJobs(int limit) {
        int bounded = Math.max(1, Math.min(limit, 10));
        return jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, bounded)).stream()
                .map(job -> new ImportJobDigest(job.getPublicId(), job.getStatus().name(),
                        job.getTotalRows(), job.getCreatedAt()))
                .toList();
    }
}
