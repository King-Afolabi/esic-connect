package com.esic.connect.attendance.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ReportDocumentRepository extends JpaRepository<ReportDocument, Long> {

    Optional<ReportDocument> findByDocumentId(String documentId);

    Page<ReportDocument> findAllByOrderByIssuedAtDesc(Pageable pageable);
}
