package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionAttendanceMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unitaires purs (sans contexte Spring) sur les invariants d'édition
 * structurelle (Lot 12) : distinction séance manuelle / planning,
 * remplacement atomique des classes, application de l'édition validée.
 */
class CourseSessionTests {

    private static final Instant STARTS_AT = Instant.parse("2026-09-10T06:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-09-10T10:00:00Z");

    @Test
    void uneSeanceCreeeManuellementEstEditable() {
        CourseSession session = new CourseSession(1L, "Titre", STARTS_AT, ENDS_AT, "Europe/Paris", "motif");
        assertThat(session.isManuallyCreated()).isTrue();
    }

    @Test
    void uneSeanceDoriginePlanningNestPasEditable() {
        CourseSession session = CourseSession.fromPlanningSlot(UUID.randomUUID(), 1L, "Titre",
                STARTS_AT, ENDS_AT, "Europe/Paris", null);
        assertThat(session.isManuallyCreated()).isFalse();
    }

    @Test
    void applyStructuralEditRemplaceTousLesChampsModifiables() {
        CourseSession session = new CourseSession(1L, "Ancien titre", STARTS_AT, ENDS_AT,
                "Europe/Paris", "ancien motif");
        session.assignSubject(10L);
        session.assignRoom("A101");

        Instant newStart = STARTS_AT.plusSeconds(3600);
        Instant newEnd = ENDS_AT.plusSeconds(3600);
        session.applyStructuralEdit(2L, "Nouveau titre", newStart, newEnd, "nouveau motif",
                20L, "B202", SessionAttendanceMode.REMOTE, "https://meet.example.org/x", 99L);

        assertThat(session.getTeacherUserId()).isEqualTo(2L);
        assertThat(session.getTitle()).isEqualTo("Nouveau titre");
        assertThat(session.getStartsAt()).isEqualTo(newStart);
        assertThat(session.getEndsAt()).isEqualTo(newEnd);
        assertThat(session.getExceptionReason()).isEqualTo("nouveau motif");
        assertThat(session.getSubjectId()).isEqualTo(20L);
        assertThat(session.getRoomCode()).isEqualTo("B202");
        assertThat(session.getAttendanceMode()).isEqualTo(SessionAttendanceMode.REMOTE);
        assertThat(session.getRemoteLink()).isEqualTo("https://meet.example.org/x");
        // Le statut, le cycle de vie et l'origine (planning / manuelle)
        // ne sont jamais touchés par une édition structurelle.
        assertThat(session.isPlanned()).isTrue();
    }

    @Test
    void applyStructuralEditVersOnSiteSupprimeLeLienDistant() {
        CourseSession session = new CourseSession(1L, "Titre", STARTS_AT, ENDS_AT, "Europe/Paris", "motif");
        session.applyStructuralEdit(1L, "Titre", STARTS_AT, ENDS_AT, "motif", null, null,
                SessionAttendanceMode.REMOTE, "https://meet.example.org/x", 1L);
        assertThat(session.getRemoteLink()).isNotNull();

        session.applyStructuralEdit(1L, "Titre", STARTS_AT, ENDS_AT, "motif", null, null,
                SessionAttendanceMode.ON_SITE, "https://meet.example.org/x", 1L);
        assertThat(session.getAttendanceMode()).isEqualTo(SessionAttendanceMode.ON_SITE);
        assertThat(session.getRemoteLink()).isNull();
    }

    @Test
    void replaceClassesViideLaCollectionEtReconstruit() {
        CourseSession session = new CourseSession(1L, "Titre", STARTS_AT, ENDS_AT, "Europe/Paris", "motif");
        session.addClass(1L);
        session.addClass(2L);
        assertThat(session.getClasses()).hasSize(2);

        session.replaceClasses(List.of(3L, 4L, 5L));

        List<Long> classGroupIds = session.getClasses().stream().map(SessionClass::getClassGroupId).toList();
        assertThat(classGroupIds).containsExactlyInAnyOrder(3L, 4L, 5L);
    }
}
