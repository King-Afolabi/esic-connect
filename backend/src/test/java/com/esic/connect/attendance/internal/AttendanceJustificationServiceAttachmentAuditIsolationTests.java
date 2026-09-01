package com.esic.connect.attendance.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Meta;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.identity.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preuve directe, hors contexte Spring et hors dispatch d'événements, que
 * {@link AttendanceJustificationService#uploadOwnAttachment} isole
 * l'échec de la trace d'audit publiée <em>après</em> un stockage réussi
 * (passe corrective G1-E/F/G, réserve A ; javadoc de la méthode).
 *
 * <p><strong>Pourquoi ce test existe en plus de
 * {@code JustificationAttachmentIntegrationTests
 * #anAuditFailureAfterTheAttachmentIsStoredStillReturns201AndKeepsThePiece}</strong> :
 * le test d'intégration simule la panne avec un {@code @EventListener} de
 * test placé en {@code Ordered.HIGHEST_PRECEDENCE}, qui lève <em>avant</em>
 * que le multicasteur Spring n'invoque le listener d'audit de production —
 * ce dernier ne s'exécute donc jamais dans ce scénario. Cela prouve qu'une
 * panne <em>quelque part</em> dans la chaîne d'écoute de l'événement est
 * isolée, mais pas que le service isole spécifiquement l'échec du
 * <em>writer</em> de production lui-même.
 *
 * <p>Ce test-ci contourne la question de l'ordre d'écoute : il mocke
 * directement {@link AttendanceChangePublisher#publishJustification}, le
 * seul point d'appel que le {@code try/catch} du service peut observer,
 * pour qu'il lève. Le comportement vérifié (retour normal, pièce déjà
 * renvoyée par le stockage) est donc garanti quel que soit le listener
 * réellement fautif en production — y compris si c'est l'écriture JPA du
 * listener d'audit lui-même qui échoue.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceJustificationServiceAttachmentAuditIsolationTests {

    private static final Long STUDENT_ID = 42L;
    private static final String STUDENT_SUBJECT = "student-subject";
    private static final UUID JUSTIFICATION_PUBLIC_ID = UUID.randomUUID();

    @Mock private CourseSessionDirectory courseSessionDirectory;
    @Mock private EnrollmentDirectory enrollmentDirectory;
    @Mock private AcademicScopeDirectory academicScope;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AttendanceRecordRepository recordRepository;
    @Mock private AttendanceJustificationRepository justificationRepository;
    @Mock private AttendanceCorrectionRepository correctionRepository;
    @Mock private AttendanceChangePublisher changePublisher;
    @Mock private JustificationAttachmentStore attachmentStore;
    @Mock private UserDirectory userDirectory;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void anAuditPublicationFailureAfterAStoredAttachmentIsIsolatedAndTheMetaIsStillReturned() {
        AttendanceJustification justification = new AttendanceJustification(1L, JustificationCategory.MEDICAL,
                null, "commentaire", STUDENT_ID, clock.instant());
        ReflectionTestUtils.setField(justification, "id", 7L);
        ReflectionTestUtils.setField(justification, "publicId", JUSTIFICATION_PUBLIC_ID);

        AttendanceJustificationService service = new AttendanceJustificationService(courseSessionDirectory,
                enrollmentDirectory, academicScope, currentUserResolver, recordRepository, justificationRepository,
                correctionRepository, changePublisher, attachmentStore, userDirectory, eventPublisher, clock);

        when(currentUserResolver.resolveInternalId(STUDENT_SUBJECT)).thenReturn(Optional.of(STUDENT_ID));
        when(justificationRepository.findByPublicId(JUSTIFICATION_PUBLIC_ID)).thenReturn(Optional.of(justification));

        Meta stored = new Meta(UUID.randomUUID(), "c.pdf", "application/pdf", 42L, "deadbeef", clock.instant());
        when(attachmentStore.store(anyLong(), eq(STUDENT_ID), eq("c.pdf"), eq("application/pdf"),
                org.mockito.ArgumentMatchers.any())).thenReturn(stored);

        // La pièce est déjà durablement stockée (le double de attachmentStore
        // vient de renvoyer sa `Meta`) quand la publication de la trace
        // d'audit échoue — panne réellement observée par le service à ce
        // point d'appel, indépendamment de tout ordre d'écoute Spring.
        Mockito.doThrow(new DataAccessResourceFailureException("panne simulée d'écriture d'audit"))
                .when(changePublisher).publishJustification(eq(JUSTIFICATION_PUBLIC_ID), eq(STUDENT_ID),
                        eq(AttendanceChangeAction.JUSTIFICATION_ATTACHMENT_STORED), isNull());

        Meta result = service.uploadOwnAttachment(JUSTIFICATION_PUBLIC_ID.toString(), "c.pdf",
                "application/pdf", new byte[] {1, 2, 3}, STUDENT_SUBJECT);

        // La panne n'a pas été laissée remonter : la pièce déjà stockée est
        // rendue à l'appelant, pas une exception.
        assertThat(result).isEqualTo(stored);
        verify(changePublisher).publishJustification(eq(JUSTIFICATION_PUBLIC_ID), eq(STUDENT_ID),
                eq(AttendanceChangeAction.JUSTIFICATION_ATTACHMENT_STORED), isNull());
    }
}
