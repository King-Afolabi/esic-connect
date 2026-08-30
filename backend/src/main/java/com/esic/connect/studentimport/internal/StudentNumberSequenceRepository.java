package com.esic.connect.studentimport.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudentNumberSequenceRepository extends JpaRepository<StudentNumberSequence, Integer> {
}
