package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.DocumentRenderer;
import com.esic.connect.document.TabularDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Génération et vérification des attestations d'assiduité (EF-REP-006,
 * AC-033 ; docs/02 §22.4).
 *
 * <p>Une attestation n'est pas un export : c'est un document qu'une
 * personne présentera à un tiers. Trois conséquences :
 *
 * <ul>
 *   <li>elle n'est produite que par un acteur autorisé, et le service
 *       applique le <strong>même périmètre</strong> que les rapports —
 *       un responsable pédagogique n'atteste que pour son périmètre ;</li>
 *   <li>elle porte un identifiant vérifiable et son émetteur, sur chaque
 *       page (AC-033) ;</li>
 *   <li>chaque émission est inscrite au registre et auditée : « génération
 *       d'attestation » figure aux opérations auditées (§23.1).</li>
 * </ul>
 *
 * <p>L'identifiant porte une part aléatoire : une séquence devinable
 * ({@code ESIC-2026-00042}) permettrait de fabriquer une référence
 * plausible, ce qui viderait la vérification de son sens.
 */
@Service
class AttestationService {

    private static final String ISSUER = "ESIC — École Supérieure d'Informatique et de Commerce";
    private static final int RANDOM_PART_LENGTH = 10;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AttendanceReportService reportService;
    private final ReportDocumentRepository repository;
    private final DocumentRenderer renderer;
    private final AttendanceActorResolver actorResolver;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String issuer;

    AttestationService(AttendanceReportService reportService,
                       ReportDocumentRepository repository,
                       DocumentRenderer renderer,
                       AttendanceActorResolver actorResolver,
                       AttendanceChangePublisher changePublisher,
                       Clock clock,
                       @Value("${app.reporting.issuer:" + ISSUER + "}") String issuer) {
        this.reportService = reportService;
        this.repository = repository;
        this.renderer = renderer;
        this.actorResolver = actorResolver;
        this.changePublisher = changePublisher;
        this.clock = clock;
        this.issuer = issuer;
    }

    /**
     * Produit l'attestation d'un apprenant sur une période et l'inscrit au
     * registre.
     *
     * @return le PDF et l'identifiant du document
     */
    @Transactional
    Attestation issue(UUID studentUserPublicId, Instant from, Instant to, String callerSubject) {
        if (studentUserPublicId == null) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        // Périmètre : `studentReport` applique déjà le filtre du rôle
        // appelant. Un apprenant hors périmètre ne produit simplement
        // aucune ligne — l'attestation est alors refusée, jamais émise vide.
        List<AttendanceReports.StudentRow> rows = reportService.studentReport(
                from, to, null, studentUserPublicId.toString(), null);
        AttendanceReports.StudentRow row = rows.stream()
                .filter(r -> studentUserPublicId.equals(r.studentUserPublicId()))
                .findFirst()
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.ATTESTATION_SUBJECT_NOT_FOUND));

        Long actorId = changePublisher.actorId(callerSubject);
        String author = Optional.ofNullable(actorId)
                .map(id -> actorResolver.lookup().displayName(id))
                .filter(name -> name != null && !name.isBlank())
                .orElse("Système ESIC Connect");

        Instant now = clock.instant();
        String documentId = nextDocumentId(now);
        TabularDocument content = AttendanceDocuments.certificate(row, from, to, clock.getZone());
        DocumentIdentity identity = new DocumentIdentity(documentId, issuer, author, now);
        byte[] pdf = renderer.toPdf(content, identity);

        repository.save(new ReportDocument(documentId, ReportDocumentType.ATTENDANCE_CERTIFICATE,
                studentUserPublicId, snapshot(row), null,
                toLocalDate(from), toLocalDate(to), actorId, author, now, sha256(pdf)));

        // Auditée comme un export : l'événement porte le type de document et
        // son identifiant, jamais le nom de l'apprenant (§23.3).
        changePublisher.publishExport(actorId,
                "report=attestation;document=" + documentId + ";from=" + from + ";to=" + to);
        return new Attestation(documentId, pdf);
    }

    /**
     * Vérification d'un document présenté (AC-033).
     *
     * <p>Ne renvoie <strong>aucune donnée d'assiduité</strong> : savoir
     * qu'un identifiant correspond à une attestation émise à telle date
     * pour telle classe suffit à un tiers, et n'expose pas le taux de
     * présence de la personne à quiconque détient le papier.
     */
    @Transactional(readOnly = true)
    Optional<AttendanceReports.DocumentCheck> verify(String documentId) {
        return repository.findByDocumentId(documentId == null ? "" : documentId.trim().toUpperCase(Locale.ROOT))
                .map(d -> new AttendanceReports.DocumentCheck(d.getDocumentId(),
                        d.getDocumentType().name(), d.getIssuedAt(), d.getIssuedBySnapshot(),
                        d.getPeriodStart(), d.getPeriodEnd(), d.getRevokedAt() != null));
    }

    @Transactional(readOnly = true)
    List<AttendanceReports.DocumentSummary> recent(int limit) {
        return repository.findAllByOrderByIssuedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .map(d -> new AttendanceReports.DocumentSummary(d.getPublicId(), d.getDocumentId(),
                        d.getDocumentType().name(), d.getSubjectSnapshot(), d.getIssuedAt(),
                        d.getIssuedBySnapshot(), d.getRevokedAt() != null))
                .getContent();
    }

    // ------------------------------------------------------------------

    private static String snapshot(AttendanceReports.StudentRow row) {
        String number = row.studentNumber() == null ? "" : " (" + row.studentNumber() + ")";
        return (nz(row.lastName()) + " " + nz(row.firstName())).trim() + number;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static LocalDate toLocalDate(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    /** {@code ESIC-ATT-<année>-<10 caractères aléatoires non ambigus>}. */
    private String nextDocumentId(Instant now) {
        StringBuilder sb = new StringBuilder("ESIC-ATT-")
                .append(now.atZone(clock.getZone()).getYear())
                .append('-');
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }

    /** PDF produit et identifiant inscrit au registre. */
    record Attestation(String documentId, byte[] pdf) {
    }
}
