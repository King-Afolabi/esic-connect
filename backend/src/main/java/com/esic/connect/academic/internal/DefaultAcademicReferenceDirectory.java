package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicReferenceDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Implémentation du port {@link AcademicReferenceDirectory}, confinée au module. */
@Component
class DefaultAcademicReferenceDirectory implements AcademicReferenceDirectory {

    private final ProgramRepository programRepository;
    private final AcademicYearRepository academicYearRepository;

    DefaultAcademicReferenceDirectory(ProgramRepository programRepository,
                                      AcademicYearRepository academicYearRepository) {
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProgramRef> findProgramByPublicId(UUID programPublicId) {
        if (programPublicId == null) {
            return Optional.empty();
        }
        return programRepository.findByPublicId(programPublicId)
                .map(DefaultAcademicReferenceDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcademicYearRef> findAcademicYearByPublicId(UUID academicYearPublicId) {
        if (academicYearPublicId == null) {
            return Optional.empty();
        }
        return academicYearRepository.findByPublicId(academicYearPublicId)
                .map(DefaultAcademicReferenceDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProgramRef> findProgramByInternalId(long programInternalId) {
        return programRepository.findById(programInternalId)
                .map(DefaultAcademicReferenceDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcademicYearRef> findAcademicYearByInternalId(long academicYearInternalId) {
        return academicYearRepository.findById(academicYearInternalId)
                .map(DefaultAcademicReferenceDirectory::toRef);
    }

    private static ProgramRef toRef(Program program) {
        return new ProgramRef(program.getId(), program.getPublicId(), program.getCode(),
                program.getName(), program.getStatus() == AcademicStatus.ACTIVE);
    }

    private static AcademicYearRef toRef(AcademicYear year) {
        return new AcademicYearRef(year.getId(), year.getPublicId(), year.getCode(),
                year.getStatus() == AcademicStatus.ACTIVE);
    }
}
