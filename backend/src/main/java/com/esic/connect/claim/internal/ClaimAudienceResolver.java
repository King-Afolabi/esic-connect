package com.esic.connect.claim.internal;

import com.esic.connect.academic.PedagogicalResponsibilityDirectory;
import com.esic.connect.claim.ClaimAudience;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Qui doit être prévenu d'un mouvement sur une réclamation (T-12).
 *
 * <p>Deux populations se cumulent :
 *
 * <ul>
 *   <li>les <strong>participants du fil</strong> — l'auteur et toute
 *       personne qui y a déjà écrit. Elles suivent le dossier et
 *       attendent une suite ;</li>
 *   <li>le <strong>guichet courant</strong> — ceux qui doivent le
 *       traiter. Un guichet n'est pas une personne : c'est ce qui rend le
 *       transfert possible, et c'est pourquoi il se résout au moment de
 *       l'événement plutôt qu'à la création.</li>
 * </ul>
 *
 * <p><strong>L'acteur est toujours retiré</strong> : personne n'a besoin
 * d'être averti de ce qu'il vient de faire.
 */
@Component
class ClaimAudienceResolver {

    private final ClaimMessageRepository messageRepository;
    private final UserDirectory userDirectory;
    private final PedagogicalResponsibilityDirectory responsibilityDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final com.esic.connect.academic.ClassGroupDirectory classGroupDirectory;
    private final Clock clock;

    ClaimAudienceResolver(ClaimMessageRepository messageRepository, UserDirectory userDirectory,
                          PedagogicalResponsibilityDirectory responsibilityDirectory,
                          CourseSessionDirectory courseSessionDirectory,
                          com.esic.connect.academic.ClassGroupDirectory classGroupDirectory,
                          Clock clock) {
        this.messageRepository = messageRepository;
        this.userDirectory = userDirectory;
        this.responsibilityDirectory = responsibilityDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.clock = clock;
    }

    Set<UUID> recipientsOf(Claim claim, Long actorInternalId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        addParticipants(claim, recipients);
        addDesk(claim, recipients);
        if (actorInternalId != null) {
            userDirectory.findByInternalId(actorInternalId)
                    .map(UserDirectory.UserRef::publicId)
                    .ifPresent(recipients::remove);
        }
        recipients.remove(null);
        return Set.copyOf(recipients);
    }

    private void addParticipants(Claim claim, Set<UUID> recipients) {
        Set<Long> participantIds = new LinkedHashSet<>();
        participantIds.add(claim.getAuthorUserId());
        messageRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId())
                .forEach(message -> participantIds.add(message.getAuthorUserId()));
        for (Long participantId : participantIds) {
            if (participantId == null) {
                continue;
            }
            userDirectory.findByInternalId(participantId)
                    .filter(ref -> !ref.archived())
                    .map(UserDirectory.UserRef::publicId)
                    .ifPresent(recipients::add);
        }
    }

    private void addDesk(Claim claim, Set<UUID> recipients) {
        LocalDate today = LocalDate.now(clock);
        switch (claim.getAudience()) {
            case SCHOOL_ADMINISTRATION ->
                    recipients.addAll(userDirectory.findActiveUserPublicIdsByRole("SCHOOL_ADMINISTRATION"));
            case PEDAGOGICAL_MANAGER -> recipients.addAll(
                    responsibilityDirectory.findManagersOfClasses(classPublicIds(claim), today));
            // Guichet formateur : le formateur de la séance visée. Sans
            // séance, la réclamation n'a pas de formateur identifiable —
            // avertir « tous les formateurs » serait absurde ; le
            // responsable du périmètre prend alors le relais.
            case TEACHER -> {
                if (claim.getCourseSessionId() != null) {
                    courseSessionDirectory.findSessionByInternalId(claim.getCourseSessionId())
                            .flatMap(ref -> courseSessionDirectory
                                    .findSessionNotificationInfo(ref.publicId()))
                            .ifPresent(info -> {
                                recipients.add(info.principalTeacherPublicId());
                                recipients.addAll(info.substituteTeacherPublicIds());
                            });
                } else {
                    recipients.addAll(responsibilityDirectory
                            .findManagersOfClasses(classPublicIds(claim), today));
                }
            }
        }
    }

    private List<UUID> classPublicIds(Claim claim) {
        if (claim.getClassGroupId() == null) {
            return List.of();
        }
        return classGroupDirectory.findByInternalId(claim.getClassGroupId())
                .map(ref -> List.of(ref.publicId()))
                .orElse(List.of());
    }
}
