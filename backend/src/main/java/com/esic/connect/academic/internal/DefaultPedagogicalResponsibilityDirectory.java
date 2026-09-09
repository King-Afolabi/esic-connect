package com.esic.connect.academic.internal;

import com.esic.connect.academic.PedagogicalResponsibilityDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution inverse du périmètre pédagogique (EF-NOTIF-003).
 *
 * <p>Le trajet est : classe → promotion → formation → affectations
 * effectives ce jour-là → comptes. Il passe par la <em>formation</em>
 * parce que c'est à ce niveau que le cahier place la responsabilité
 * (§6.5) : un responsable répond d'une formation entière, jamais d'une
 * classe isolée.
 *
 * <p>Un compte archivé est écarté : il ne peut plus être destinataire.
 */
@Component
class DefaultPedagogicalResponsibilityDirectory implements PedagogicalResponsibilityDirectory {

    private final ClassGroupRepository classGroupRepository;
    private final PedagogicalAssignmentRepository assignmentRepository;
    private final UserDirectory userDirectory;

    DefaultPedagogicalResponsibilityDirectory(ClassGroupRepository classGroupRepository,
                                              PedagogicalAssignmentRepository assignmentRepository,
                                              UserDirectory userDirectory) {
        this.classGroupRepository = classGroupRepository;
        this.assignmentRepository = assignmentRepository;
        this.userDirectory = userDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findManagersOfClasses(Collection<UUID> classGroupPublicIds, LocalDate on) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty() || on == null) {
            return Set.of();
        }
        Set<Long> programIds = new LinkedHashSet<>();
        for (UUID classPublicId : classGroupPublicIds) {
            if (classPublicId == null) {
                continue;
            }
            classGroupRepository.findByPublicId(classPublicId)
                    .map(cg -> cg.getPromotion().getProgram().getId())
                    .ifPresent(programIds::add);
        }
        if (programIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> managers = new LinkedHashSet<>();
        for (Long managerUserId : assignmentRepository.findEffectiveManagerIds(
                programIds, PedagogicalAssignmentStatus.ACTIVE, on)) {
            Optional<UserDirectory.UserRef> ref = userDirectory.findByInternalId(managerUserId);
            ref.filter(user -> !user.archived())
                    .map(UserDirectory.UserRef::publicId)
                    .filter(Objects::nonNull)
                    .ifPresent(managers::add);
        }
        return Set.copyOf(managers);
    }
}
