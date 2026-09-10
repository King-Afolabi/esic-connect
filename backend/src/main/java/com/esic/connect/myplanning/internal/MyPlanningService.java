package com.esic.connect.myplanning.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import com.esic.connect.identity.UserDirectory.PersonName;
import com.esic.connect.myplanning.internal.MyPlanningResponses.ClassView;
import com.esic.connect.myplanning.internal.MyPlanningResponses.MyPlanning;
import com.esic.connect.myplanning.internal.MyPlanningResponses.SessionLine;
import com.esic.connect.myplanning.internal.MyPlanningResponses.TeacherView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assemble « Mon planning » (CDC §5.6/§5.7) : lecture seule, périmètre
 * décidé côté serveur à partir du rôle effectif — jamais d'un paramètre
 * client. Voir {@code package-info.java} pour le détail des ports lus.
 */
@Service
class MyPlanningService {

    /** Borne haute d'un appel : un formateur/apprenant consulte, n'exporte pas en masse. */
    private static final int LIMIT = 500;

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;

    MyPlanningService(CourseSessionDirectory courseSessionDirectory,
                      EnrollmentDirectory enrollmentDirectory,
                      ClassGroupDirectory classGroupDirectory,
                      UserDirectory userDirectory) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
    }

    @Transactional(readOnly = true)
    MyPlanning forCaller(String subject, List<String> roleCodes, Instant from, Instant to) {
        UUID userPublicId = parseUuid(subject);
        // Priorité formateur > apprenant : un compte ne porte normalement
        // qu'un seul de ces deux rôles ; s'il portait les deux, la vue
        // formateur (gestion) est la plus utile.
        if (roleCodes.contains("TEACHER")) {
            List<SessionRef> sessions = userPublicId == null ? List.of()
                    : courseSessionDirectory.findTeacherSchedule(userPublicId, from, to, LIMIT);
            return new MyPlanning("TEACHER", lines(sessions));
        }
        if (roleCodes.contains("STUDENT")) {
            Set<UUID> classIds = userPublicId == null ? Set.of() : studentClassIds(userPublicId);
            List<SessionRef> sessions = classIds.isEmpty() ? List.of()
                    : courseSessionDirectory.findClassSchedule(classIds, from, to, LIMIT);
            return new MyPlanning("STUDENT", lines(sessions));
        }
        // @PreAuthorize exclut déjà ce cas ; défense en profondeur.
        return new MyPlanning("NONE", List.of());
    }

    private Set<UUID> studentClassIds(UUID userPublicId) {
        return enrollmentDirectory.findEnrollmentsForUser(userPublicId).stream()
                .filter(EnrollmentDirectory.EnrollmentRef::usable)
                .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<SessionLine> lines(List<SessionRef> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> teacherIds = new LinkedHashSet<>();
        Set<UUID> classPublicIds = new LinkedHashSet<>();
        for (SessionRef s : sessions) {
            teacherIds.add(s.teacherUserId());
            classPublicIds.addAll(s.classGroupPublicIds());
        }
        Map<Long, PersonName> teacherNames = userDirectory.findNames(teacherIds);
        Map<Long, UUID> teacherPublicIds = new HashMap<>();
        for (long id : teacherIds) {
            userDirectory.findByInternalId(id).ifPresent(ref -> teacherPublicIds.put(id, ref.publicId()));
        }
        Map<UUID, String> classCodes = new HashMap<>();
        for (UUID classPublicId : classPublicIds) {
            classGroupDirectory.findByPublicId(classPublicId)
                    .ifPresent(ref -> classCodes.put(classPublicId, ref.code()));
        }

        return sessions.stream().map(s -> {
            PersonName name = teacherNames.get(s.teacherUserId());
            TeacherView teacher = new TeacherView(teacherPublicIds.get(s.teacherUserId()),
                    name != null ? name.firstName() : null, name != null ? name.lastName() : null);
            List<ClassView> classes = s.classGroupPublicIds().stream()
                    .map(id -> new ClassView(id, classCodes.get(id)))
                    .toList();
            return new SessionLine(s.publicId(), s.title(), s.status().name(), s.startsAt(), s.endsAt(),
                    s.timeZoneId(), teacher, classes, s.roomCode());
        }).toList();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
