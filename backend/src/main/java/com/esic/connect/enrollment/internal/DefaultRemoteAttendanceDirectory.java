package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.RemoteAttendanceDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Implémentation du port {@link RemoteAttendanceDirectory}. */
@Component
class DefaultRemoteAttendanceDirectory implements RemoteAttendanceDirectory {

    private final RemoteAttendanceAuthorizationRepository repository;
    private final UserDirectory userDirectory;
    private final ClassGroupDirectory classGroupDirectory;

    DefaultRemoteAttendanceDirectory(RemoteAttendanceAuthorizationRepository repository,
                                     UserDirectory userDirectory,
                                     ClassGroupDirectory classGroupDirectory) {
        this.repository = repository;
        this.userDirectory = userDirectory;
        this.classGroupDirectory = classGroupDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRemoteAttendanceAuthorized(UUID studentUserPublicId, UUID classGroupPublicId,
                                                LocalDate date) {
        if (studentUserPublicId == null || date == null) {
            return false;
        }
        Optional<UserDirectory.UserRef> student = userDirectory.findByPublicId(studentUserPublicId);
        if (student.isEmpty()) {
            return false;
        }
        Long classInternalId = classGroupPublicId == null ? null
                : classGroupDirectory.findByPublicId(classGroupPublicId)
                        .map(ClassGroupDirectory.ClassGroupRef::internalId).orElse(null);

        return repository.findCovering(student.get().internalId(), date).stream()
                // Une autorisation sans classe est générale ; une
                // autorisation rattachée à une classe ne vaut que pour
                // elle. Refuser silencieusement l'inverse serait pire que
                // de refuser tout court.
                .anyMatch(authorization -> authorization.getClassGroupId() == null
                        || authorization.getClassGroupId().equals(classInternalId));
    }
}
