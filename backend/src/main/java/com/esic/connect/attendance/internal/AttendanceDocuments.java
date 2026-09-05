package com.esic.connect.attendance.internal;

import com.esic.connect.document.TabularDocument;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conversion des lignes de rapport en {@link TabularDocument}
 * (EF-REP-003, EF-REP-004, EF-REP-005).
 *
 * <p>Un seul endroit décrit le contenu de chaque rapport, afin que le
 * CSV, le classeur et le PDF d'un même rapport ne puissent pas diverger :
 * une colonne ajoutée au CSV et oubliée au PDF se lirait comme une
 * différence de chiffres.
 *
 * <p>Le formatage (taux en pourcentage, dates lisibles) est fait ici et
 * pas dans le module {@code document} : c'est une décision métier.
 */
final class AttendanceDocuments {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private AttendanceDocuments() {
    }

    static TabularDocument sessions(List<AttendanceReports.SessionRow> rows, Instant from, Instant to,
                                    ZoneId zone) {
        List<List<String>> body = new ArrayList<>(rows.size());
        for (AttendanceReports.SessionRow r : rows) {
            body.add(List.of(str(r.sessionPublicId()), nz(r.sessionTitle()), dateTime(r.startsAt(), zone),
                    dateTime(r.endsAt(), zone), nz(r.classCodes()), nz(r.teacherName()),
                    Integer.toString(r.checkpointCount()), Long.toString(r.expectedCount()),
                    Integer.toString(r.presentCount()), Integer.toString(r.lateCount()),
                    Integer.toString(r.absentCount()), Integer.toString(r.excusedCount()),
                    pct(r.attendanceRate())));
        }
        return new TabularDocument("Rapport d'assiduité par séance", period(from, to, zone),
                List.of(new TabularDocument.Fact("Séances", Integer.toString(rows.size()))),
                List.of("Identifiant de séance", "Titre", "Début", "Fin", "Classes", "Formateur",
                        "Points de contrôle", "Attendu", "Présent", "Retard", "Absent", "Excusé",
                        "Taux de présence (%)"),
                body, NOTES);
    }

    static TabularDocument classes(List<AttendanceReports.ClassRow> rows, Instant from, Instant to,
                                   ZoneId zone) {
        List<List<String>> body = new ArrayList<>(rows.size());
        for (AttendanceReports.ClassRow r : rows) {
            AttendanceReports.HalfDayTotals t = r.totals();
            body.add(List.of(nz(r.classCode()), Integer.toString(r.studentCount()),
                    Long.toString(t.expectedHalfDays()), Long.toString(t.presentHalfDays()),
                    Long.toString(t.absentHalfDays()), Long.toString(t.excusedHalfDays()),
                    Long.toString(t.companyHalfDays()), Long.toString(t.unknownHalfDays()),
                    Long.toString(t.lateCount()), pct(t.attendanceRate()),
                    pct(t.unjustifiedAbsenceRate())));
        }
        return new TabularDocument("Rapport d'assiduité par classe", period(from, to, zone),
                List.of(new TabularDocument.Fact("Classes", Integer.toString(rows.size()))),
                List.of("Classe", "Effectif", "Demi-journées attendues", "Présentes", "Absentes",
                        "Excusées", "Entreprise", "Inconnues", "Retards", "Taux de présence (%)",
                        "Taux d'absence injustifiée (%)"),
                body, NOTES);
    }

    static TabularDocument students(List<AttendanceReports.StudentRow> rows, Instant from, Instant to,
                                    ZoneId zone) {
        List<List<String>> body = new ArrayList<>(rows.size());
        for (AttendanceReports.StudentRow r : rows) {
            AttendanceReports.HalfDayTotals t = r.totals();
            body.add(List.of(nz(r.studentNumber()), nz(r.lastName()), nz(r.firstName()), nz(r.classCode()),
                    Long.toString(t.expectedHalfDays()), Long.toString(t.presentHalfDays()),
                    Long.toString(t.absentHalfDays()), Long.toString(t.excusedHalfDays()),
                    Long.toString(t.companyHalfDays()), Long.toString(t.unknownHalfDays()),
                    Long.toString(t.lateCount()), pct(t.attendanceRate()),
                    pct(t.unjustifiedAbsenceRate())));
        }
        return new TabularDocument("Rapport d'assiduité par apprenant", period(from, to, zone),
                List.of(new TabularDocument.Fact("Apprenants", Integer.toString(rows.size()))),
                List.of("Numéro étudiant", "Nom", "Prénom", "Classe", "Demi-journées attendues",
                        "Présentes", "Absentes", "Excusées", "Entreprise", "Inconnues", "Retards",
                        "Taux de présence (%)", "Taux d'absence injustifiée (%)"),
                body, NOTES);
    }

    /**
     * Attestation d'assiduité d'un apprenant (EF-REP-006, AC-019, AC-033).
     *
     * <p>La mesure est exprimée en <strong>demi-journées et en
     * journées</strong> — l'unité du cahier (§22.1) — et non en heures :
     * une attestation qui compterait des heures de connexion dirait autre
     * chose que ce que le produit sait mesurer.
     */
    static TabularDocument certificate(AttendanceReports.StudentRow row, Instant from, Instant to,
                                       ZoneId zone) {
        AttendanceReports.HalfDayTotals t = row.totals();
        List<TabularDocument.Fact> facts = List.of(
                new TabularDocument.Fact("Apprenant", nz(row.lastName()) + " " + nz(row.firstName())),
                new TabularDocument.Fact("Numéro étudiant", nz(row.studentNumber())),
                new TabularDocument.Fact("Classe", nz(row.classCode())),
                new TabularDocument.Fact("Période", period(from, to, zone)),
                new TabularDocument.Fact("Demi-journées attendues", Long.toString(t.expectedHalfDays())),
                new TabularDocument.Fact("Demi-journées suivies", Long.toString(t.presentHalfDays())),
                new TabularDocument.Fact("Journées équivalentes suivies", days(t.presentHalfDays())),
                new TabularDocument.Fact("Demi-journées excusées", Long.toString(t.excusedHalfDays())),
                new TabularDocument.Fact("Demi-journées absentes", Long.toString(t.absentHalfDays())),
                new TabularDocument.Fact("Demi-journées en entreprise", Long.toString(t.companyHalfDays())),
                new TabularDocument.Fact("Retards constatés", Long.toString(t.lateCount())),
                new TabularDocument.Fact("Taux d'assiduité", pct(t.attendanceRate()) + " %"));

        List<String> notes = new ArrayList<>(CERTIFICATE_NOTES);
        if (t.unknownHalfDays() > 0) {
            notes.add("Sur la période, " + t.unknownHalfDays() + " demi-journée(s) n'ont pas de rythme "
                    + "d'alternance résolu : elles sont comptées à part et jamais comme une absence.");
        }
        // Aucune table : une attestation atteste d'un volume d'assiduité,
        // pas d'un relevé de séances. Le détail par séance est l'objet du
        // rapport individuel (EF-REP-002), qui s'exporte à part.
        return new TabularDocument("Attestation d'assiduité", period(from, to, zone), facts,
                List.of(), List.of(), notes);
    }

    private static final List<String> NOTES = List.of(
            "Unité de calcul : la demi-journée (deux demi-journées valident une journée).",
            "Les demi-journées passées en entreprise sont exclues du dénominateur : "
                    + "une période en alternance n'est jamais comptée comme une absence.",
            "Les demi-journées sans rythme d'alternance résolu sont comptées séparément.");

    private static final List<String> CERTIFICATE_NOTES = List.of(
            "Unité de calcul : la demi-journée. Deux demi-journées validées valent une journée.",
            "Une période en entreprise n'est jamais comptée comme une absence.",
            "Une absence excusée reste une absence dans l'historique : elle est justifiée, pas effacée.");

    // ------------------------------------------------------------------

    static String period(Instant from, Instant to, ZoneId zone) {
        if (from == null && to == null) {
            return "Toute la période disponible";
        }
        String start = from == null ? "origine" : DATE.format(ZonedDateTime.ofInstant(from, zone));
        String end = to == null ? "aujourd'hui" : DATE.format(ZonedDateTime.ofInstant(to, zone));
        return "Du " + start + " au " + end;
    }

    private static String days(long halfDays) {
        // Une demi-journée vaut 0,5 jour ; l'écriture décimale est celle du
        // cahier (§22.1), pas une conversion en heures.
        return String.format(Locale.FRENCH, "%.1f", halfDays / 2.0d);
    }

    private static String dateTime(Instant instant, ZoneId zone) {
        return instant == null ? "" : DATE_TIME.format(ZonedDateTime.ofInstant(instant, zone));
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String pct(double ratio) {
        return String.format(Locale.ROOT, "%.2f", ratio * 100d);
    }
}
