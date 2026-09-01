package com.esic.connect.academic.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link ClassGroupDirectory}. Reste confinée à
 * {@code academic.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link ClassGroupDirectory.ClassGroupRef}.
 *
 * <p>{@code openForEnrollment} n'est vrai que si la classe <em>et</em>
 * toute sa chaîne de rattachement (promotion, formation, année scolaire)
 * sont {@link AcademicStatus#ACTIVE} — l'appelant s'appuie dessus pour
 * refuser une inscription sous un parent archivé (docs/04 §13, §12.5).
 */
@Component
class DefaultClassGroupDirectory implements ClassGroupDirectory {

    private final ClassGroupRepository classGroupRepository;
    private final ProgramRepository programRepository;
    private final AcademicYearRepository academicYearRepository;

    DefaultClassGroupDirectory(ClassGroupRepository classGroupRepository,
                               ProgramRepository programRepository,
                               AcademicYearRepository academicYearRepository) {
        this.classGroupRepository = classGroupRepository;
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassGroupRef> findByPublicId(UUID classGroupPublicId) {
        if (classGroupPublicId == null) {
            return Optional.empty();
        }
        return classGroupRepository.findByPublicId(classGroupPublicId).map(DefaultClassGroupDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassGroupRef> findByInternalId(long classGroupInternalId) {
        return classGroupRepository.findById(classGroupInternalId).map(DefaultClassGroupDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassGroupRef> findByInternalIds(java.util.Collection<Long> classGroupInternalIds) {
        if (classGroupInternalIds == null || classGroupInternalIds.isEmpty()) {
            return List.of();
        }
        return classGroupRepository.findAllById(classGroupInternalIds).stream()
                .map(DefaultClassGroupDirectory::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassGroupRef> findByPublicIds(java.util.Collection<UUID> classGroupPublicIds) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = classGroupPublicIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return classGroupRepository.findByPublicIdIn(ids).stream()
                .map(DefaultClassGroupDirectory::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassGroupResolution resolveForImport(String programCode, String classCode, String academicYearCode) {
        String program = trimOrEmpty(programCode);
        String klass = trimOrEmpty(classCode);
        String year = trimOrEmpty(academicYearCode);

        Program resolvedProgram = program.isEmpty() ? null
                : programRepository.findByCodeIgnoreCase(program).orElse(null);
        if (resolvedProgram == null) {
            return ClassGroupResolution.Miss.PROGRAM_UNKNOWN;
        }
        AcademicYear resolvedYear = year.isEmpty() ? null
                : academicYearRepository.findByCodeIgnoreCase(year).orElse(null);
        if (resolvedYear == null) {
            return ClassGroupResolution.Miss.ACADEMIC_YEAR_UNKNOWN;
        }

        List<ClassGroup> candidates = klass.isEmpty() ? List.of()
                : classGroupRepository.findByCodeIgnoreCase(klass);
        if (candidates.isEmpty()) {
            return ClassGroupResolution.Miss.CLASS_UNKNOWN;
        }
        List<ClassGroup> inProgram = candidates.stream()
                .filter(cg -> cg.getPromotion().getProgram().getId().equals(resolvedProgram.getId()))
                .toList();
        if (inProgram.isEmpty()) {
            return ClassGroupResolution.Miss.CLASS_NOT_IN_PROGRAM;
        }
        ClassGroup classGroup = inProgram.stream()
                .filter(cg -> cg.getPromotion().getAcademicYear().getId().equals(resolvedYear.getId()))
                .findFirst()
                .orElse(null);
        if (classGroup == null) {
            return ClassGroupResolution.Miss.CLASS_NOT_IN_YEAR;
        }
        ClassGroupRef ref = toRef(classGroup);
        if (!ref.openForEnrollment()) {
            return ClassGroupResolution.Miss.CHAIN_ARCHIVED;
        }
        return new ClassGroupResolution.Found(ref, resolvedYear.getStartDate().getYear());
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static ClassGroupRef toRef(ClassGroup classGroup) {
        Promotion promotion = classGroup.getPromotion();
        Program program = promotion.getProgram();
        AcademicYear academicYear = promotion.getAcademicYear();
        boolean openForEnrollment = classGroup.getStatus() == AcademicStatus.ACTIVE
                && promotion.getStatus() == AcademicStatus.ACTIVE
                && program.getStatus() == AcademicStatus.ACTIVE
                && academicYear.getStatus() == AcademicStatus.ACTIVE;
        return new ClassGroupRef(
                classGroup.getId(),
                classGroup.getPublicId(),
                classGroup.getCode(),
                program.getPublicId(),
                program.getCode(),
                academicYear.getId(),
                academicYear.getPublicId(),
                academicYear.getCode(),
                openForEnrollment);
    }
}
