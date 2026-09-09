package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface SubjectProgramRepository extends JpaRepository<SubjectProgram, Long> {

    List<SubjectProgram> findBySubjectId(Long subjectId);

    List<SubjectProgram> findBySubjectIdIn(Collection<Long> subjectIds);

    List<SubjectProgram> findByProgramId(Long programId);

    void deleteBySubjectId(Long subjectId);
}
