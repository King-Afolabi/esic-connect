package com.esic.connect.myplanning.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue API du planning de l'appelant — jamais d'identifiant SQL interne.
 */
final class MyPlanningResponses {

    private MyPlanningResponses() {
    }

    record MyPlanning(String role, List<SessionLine> sessions) {
    }

    record SessionLine(
            UUID sessionPublicId,
            String title,
            String status,
            Instant startsAt,
            Instant endsAt,
            String timeZoneId,
            TeacherView teacher,
            List<ClassView> classes,
            String roomCode) {
    }

    record TeacherView(UUID publicId, String firstName, String lastName) {
    }

    record ClassView(UUID publicId, String code) {
    }
}
