package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Autorisation d'un suivi à distance individuel (EF-ENR-004 ; docs/02
 * §15.3 : « un apprenant peut être autorisé à distance alors que sa
 * classe est en présentiel »).
 *
 * <p>La décision appartient au responsable pédagogique ou à
 * l'administration (docs/02 §5.5). Le formateur peut signaler une
 * exception, jamais l'accorder : c'est la même logique que le
 * remplacement — il propose, il ne valide pas.
 */
@Service
class RemoteAttendanceService {

    private final RemoteAttendanceAuthorizationRepository repository;
    private final UserDirectory userDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final EnrollmentChangePublisher changePublisher;
    private final Clock clock;

    RemoteAttendanceService(RemoteAttendanceAuthorizationRepository repository,
                            UserDirectory userDirectory,
                            ClassGroupDirectory classGroupDirectory,
                            AcademicScopeDirectory academicScopeDirectory,
                            EnrollmentChangePublisher changePublisher,
                            Clock clock) {
        this.repository = repository;
        this.userDirectory = userDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional
    RemoteAttendanceResponse authorize(RemoteAttendanceRequests.Authorize request,
                                       String callerSubject) {
        UUID studentPublicId = EnrollmentWeb.parseUuid(request.studentUserPublicId(),
                EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        UserDirectory.UserRef student = userDirectory.findByPublicId(studentPublicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
        if (student.archived() || !student.activeRoles().contains("STUDENT")) {
            // Autoriser un compte sans rôle apprenant produirait une
            // autorisation qui ne s'appliquerait jamais : autant la refuser
            // tout de suite, avec un motif compréhensible.
            throw new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        }

        Long classInternalId = null;
        UUID classPublicId = null;
        if (request.classGroupPublicId() != null && !request.classGroupPublicId().isBlank()) {
            classPublicId = EnrollmentWeb.parseUuid(request.classGroupPublicId(),
                    EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND);
            ClassGroupDirectory.ClassGroupRef classGroup = classGroupDirectory
                    .findByPublicId(classPublicId)
                    .orElseThrow(() -> new EnrollmentException(
                            EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND));
            requireScope(classPublicId);
            classInternalId = classGroup.internalId();
        } else if (!academicScopeDirectory.hasGlobalScope()) {
            // Une autorisation GÉNÉRALE dépasse par nature le périmètre d'un
            // responsable pédagogique : elle vaudrait pour les classes d'un
            // autre. Seul un périmètre global peut l'accorder.
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }

        if (request.validUntil() != null && request.validUntil().isBefore(request.validFrom())) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_REMOTE_PERIOD);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        if (actorId == null) {
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }

        RemoteAttendanceAuthorization authorization = new RemoteAttendanceAuthorization(
                student.internalId(), classInternalId, request.reason().trim(),
                request.validFrom(), request.validUntil(), actorId, clock.instant());
        RemoteAttendanceAuthorization saved = repository.saveAndFlush(authorization);

        changePublisher.publish(EnrollmentResourceType.REMOTE_ATTENDANCE_AUTHORIZATION,
                saved.getPublicId(), EnrollmentChangeAction.CREATED, actorId,
                "student=" + studentPublicId + ";from=" + request.validFrom()
                        + ";until=" + (request.validUntil() == null ? "-" : request.validUntil()));
        return toResponse(saved, studentPublicId, classPublicId);
    }

    @Transactional
    RemoteAttendanceResponse revoke(UUID publicId, RemoteAttendanceRequests.Revoke request,
                                    String callerSubject) {
        RemoteAttendanceAuthorization authorization = repository.findByPublicId(publicId)
                .orElseThrow(() -> new EnrollmentException(
                        EnrollmentException.Kind.ENROLLMENT_NOT_FOUND));
        requireScopeOf(authorization);
        if (!authorization.isActive()) {
            // Révoquer deux fois n'a pas de sens : la seconde décision
            // écraserait le motif de la première sans rien changer.
            throw new EnrollmentException(EnrollmentException.Kind.AUTHORIZATION_NOT_ACTIVE);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        authorization.revoke(request.reason().trim(), actorId, clock.instant());
        RemoteAttendanceAuthorization saved = repository.saveAndFlush(authorization);

        changePublisher.publish(EnrollmentResourceType.REMOTE_ATTENDANCE_AUTHORIZATION,
                saved.getPublicId(), EnrollmentChangeAction.REVOKED, actorId, null);
        return toResponse(saved, studentPublicIdOf(saved), classPublicIdOf(saved));
    }

    @Transactional(readOnly = true)
    List<RemoteAttendanceResponse> listForStudent(UUID studentUserPublicId) {
        UserDirectory.UserRef student = userDirectory.findByPublicId(studentUserPublicId)
                .orElseThrow(() -> new EnrollmentException(
                        EnrollmentException.Kind.STUDENT_NOT_FOUND));
        return repository.findByStudentUserIdOrderByValidFromDesc(student.internalId()).stream()
                .filter(this::inScope)
                .map(authorization -> toResponse(authorization, studentUserPublicId,
                        classPublicIdOf(authorization)))
                .toList();
    }

    // ------------------------------------------------------------------

    private void requireScope(UUID classGroupPublicId) {
        if (!academicScopeDirectory.hasGlobalScope()
                && !academicScopeDirectory.isClassInScope(classGroupPublicId)) {
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }
    }

    private void requireScopeOf(RemoteAttendanceAuthorization authorization) {
        if (!inScope(authorization)) {
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }
    }

    private boolean inScope(RemoteAttendanceAuthorization authorization) {
        if (academicScopeDirectory.hasGlobalScope()) {
            return true;
        }
        UUID classPublicId = classPublicIdOf(authorization);
        // Une autorisation générale n'appartient à aucune classe : hors du
        // périmètre d'un responsable pédagogique, par construction.
        return classPublicId != null && academicScopeDirectory.isClassInScope(classPublicId);
    }

    private UUID studentPublicIdOf(RemoteAttendanceAuthorization authorization) {
        return userDirectory.findByInternalId(authorization.getStudentUserId())
                .map(UserDirectory.UserRef::publicId).orElse(null);
    }

    private UUID classPublicIdOf(RemoteAttendanceAuthorization authorization) {
        return authorization.getClassGroupId() == null ? null
                : classGroupDirectory.findByInternalId(authorization.getClassGroupId())
                        .map(ClassGroupDirectory.ClassGroupRef::publicId).orElse(null);
    }

    private static RemoteAttendanceResponse toResponse(RemoteAttendanceAuthorization authorization,
                                                       UUID studentPublicId, UUID classPublicId) {
        return new RemoteAttendanceResponse(authorization.getPublicId(), studentPublicId,
                classPublicId, authorization.getStatus().name(), authorization.getReason(),
                authorization.getValidFrom(), authorization.getValidUntil(),
                authorization.getDecidedAt(), authorization.getRevokedAt(),
                authorization.getRevocationReason(), authorization.getCreatedAt());
    }
}
