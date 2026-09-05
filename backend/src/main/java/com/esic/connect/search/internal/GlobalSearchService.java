package com.esic.connect.search.internal;

import com.esic.connect.academic.AcademicReferenceDirectory;
import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.TeacherDirectory;
import com.esic.connect.organization.RoomDirectory;
import com.esic.connect.shared.SearchPattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Recherche globale (EF-USER-009 ; docs/02 §22.7).
 *
 * <p><strong>Le périmètre est décidé ici.</strong> Un appelant à
 * périmètre global (administration) cherche partout ; un responsable
 * pédagogique ne voit que ses classes, leurs apprenants et leurs
 * séances. Le périmètre n'est jamais un paramètre d'appel : il est
 * relu du contexte de sécurité à chaque recherche.
 *
 * <p>Chaque catégorie est bornée séparément. Une seule catégorie
 * saturée ne doit pas effacer les autres du résultat : trouver
 * vingt-cinq apprenants ne doit pas empêcher de voir la salle qui porte
 * le même nom.
 */
@Service
class GlobalSearchService {

    /** Borne par catégorie ; le total reste donc borné lui aussi. */
    private static final int PER_CATEGORY = 8;
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private final EnrollmentDirectory enrollmentDirectory;
    private final TeacherDirectory teacherDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicReferenceDirectory academicReferenceDirectory;
    private final AcademicScopeDirectory academicScope;
    private final RoomDirectory roomDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final Clock clock;

    GlobalSearchService(EnrollmentDirectory enrollmentDirectory,
                        TeacherDirectory teacherDirectory,
                        ClassGroupDirectory classGroupDirectory,
                        AcademicReferenceDirectory academicReferenceDirectory,
                        AcademicScopeDirectory academicScope,
                        RoomDirectory roomDirectory,
                        CourseSessionDirectory courseSessionDirectory,
                        Clock clock) {
        this.enrollmentDirectory = enrollmentDirectory;
        this.teacherDirectory = teacherDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.academicReferenceDirectory = academicReferenceDirectory;
        this.academicScope = academicScope;
        this.roomDirectory = roomDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    SearchResponses.GlobalSearch search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        List<String> notes = new ArrayList<>();
        if (SearchPattern.of(query) == null) {
            // Un fragment d'un caractère balaierait la base entière : ce
            // n'est pas une recherche, c'est une énumération.
            notes.add("Saisissez au moins " + SearchPattern.MIN_LENGTH + " caractères.");
            return new SearchResponses.GlobalSearch(query, false, List.of(), notes);
        }

        Optional<Set<Long>> visibleClassInternalIds = academicScope.visibleClassGroupIds();
        Optional<Set<Long>> visibleProgramInternalIds = academicScope.visibleProgramIds();
        boolean global = visibleClassInternalIds.isEmpty();
        if (!global) {
            notes.add("Résultats limités à votre périmètre pédagogique.");
        }
        notes.add("L'adresse électronique n'est pas un critère de recherche.");

        Set<Long> classScope = visibleClassInternalIds.orElse(null);
        Set<Long> programScope = visibleProgramInternalIds.orElse(null);
        Set<UUID> classPublicScope = classScope == null ? null : publicIdsOf(classScope);

        List<SearchResponses.Hit> hits = new ArrayList<>();
        boolean truncated = false;

        List<ClassGroupDirectory.ClassGroupRef> classes =
                classGroupDirectory.search(query, classScope, PER_CATEGORY);
        truncated |= classes.size() >= PER_CATEGORY;
        for (ClassGroupDirectory.ClassGroupRef c : classes) {
            hits.add(new SearchResponses.Hit("CLASS_GROUP", c.publicId().toString(), c.code(),
                    join(c.programCode(), c.academicYearCode())));
        }

        List<AcademicReferenceDirectory.ProgramRef> programs =
                academicReferenceDirectory.searchPrograms(query, programScope, PER_CATEGORY);
        truncated |= programs.size() >= PER_CATEGORY;
        for (AcademicReferenceDirectory.ProgramRef p : programs) {
            hits.add(new SearchResponses.Hit("PROGRAM", p.publicId().toString(), p.code(), p.name()));
        }

        List<EnrollmentDirectory.RosterEntry> students =
                enrollmentDirectory.searchStudents(query, classPublicScope, PER_CATEGORY);
        truncated |= students.size() >= PER_CATEGORY;
        for (EnrollmentDirectory.RosterEntry s : students) {
            String label = join(s.lastName(), s.firstName());
            String secondary = join(s.studentNumber(), s.classGroupCode());
            hits.add(new SearchResponses.Hit("STUDENT",
                    s.studentProfilePublicId() == null ? "" : s.studentProfilePublicId().toString(),
                    label, secondary));
        }

        // Les formateurs et les salles ne sont pas périmétrés : ils
        // appartiennent à l'établissement, pas à une formation. Un
        // responsable qui cherche un formateur en trouve donc un — mais
        // il n'atteindra pas ses séances hors périmètre.
        List<TeacherDirectory.TeacherRef> teachers =
                teacherDirectory.searchEligibleTeachers(query, PER_CATEGORY);
        truncated |= teachers.size() >= PER_CATEGORY;
        for (TeacherDirectory.TeacherRef t : teachers) {
            hits.add(new SearchResponses.Hit("TEACHER", t.publicId().toString(),
                    join(t.lastName(), t.firstName()), "Formateur"));
        }

        List<RoomDirectory.RoomSearchRef> rooms = roomDirectory.search(query, PER_CATEGORY);
        truncated |= rooms.size() >= PER_CATEGORY;
        for (RoomDirectory.RoomSearchRef r : rooms) {
            hits.add(new SearchResponses.Hit("ROOM", r.publicId().toString(), r.code(),
                    join(r.name(), r.buildingName())));
        }

        List<CourseSessionDirectory.SessionRef> sessions =
                courseSessionDirectory.searchSessions(query, classPublicScope, PER_CATEGORY);
        truncated |= sessions.size() >= PER_CATEGORY;
        ZoneId zone = clock.getZone();
        for (CourseSessionDirectory.SessionRef s : sessions) {
            hits.add(new SearchResponses.Hit("SESSION", s.publicId().toString(),
                    s.title() == null || s.title().isBlank() ? "Séance" : s.title(),
                    s.startsAt() == null ? null : DAY.format(ZonedDateTime.ofInstant(s.startsAt(), zone))));
        }

        return new SearchResponses.GlobalSearch(query, truncated, hits, notes);
    }

    private Set<UUID> publicIdsOf(Set<Long> classInternalIds) {
        if (classInternalIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(classInternalIds)) {
            ids.add(ref.publicId());
        }
        return ids;
    }

    private static String join(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) {
            return b.isEmpty() ? null : b;
        }
        return b.isEmpty() ? a : a + " — " + b;
    }
}
