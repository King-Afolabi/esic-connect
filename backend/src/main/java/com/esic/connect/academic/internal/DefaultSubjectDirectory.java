package com.esic.connect.academic.internal;

import com.esic.connect.academic.SubjectDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Implémentation du port {@link SubjectDirectory}, confinée au module. */
@Component
class DefaultSubjectDirectory implements SubjectDirectory {

    private final SubjectRepository repository;

    DefaultSubjectDirectory(SubjectRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectRef> findByPublicId(UUID subjectPublicId) {
        if (subjectPublicId == null) {
            return Optional.empty();
        }
        return repository.findByPublicId(subjectPublicId).map(DefaultSubjectDirectory::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubjectRef> findByInternalId(long subjectInternalId) {
        return repository.findById(subjectInternalId).map(DefaultSubjectDirectory::toRef);
    }

    private static SubjectRef toRef(Subject subject) {
        return new SubjectRef(subject.getId(), subject.getPublicId(), subject.getCode(),
                subject.getName(), subject.getStatus() == AcademicStatus.ACTIVE);
    }
}
