package com.esic.connect.academic.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    DefaultClassGroupDirectory(ClassGroupRepository classGroupRepository) {
        this.classGroupRepository = classGroupRepository;
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
