package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface StudentImportRowCorrectionRepository extends JpaRepository<StudentImportRowCorrection, Long> {

    List<StudentImportRowCorrection> findByRowIdOrderByCorrectedAtAsc(Long rowId);
}
