package com.esic.connect.identity.internal;

import com.esic.connect.document.TabularDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rapport des invitations non activées (EF-REP-010 ; docs/02 §22.3 —
 * « comptes en attente, dernière relance »).
 *
 * <p>Le rapport <strong>ne contient pas l'adresse électronique en
 * clair</strong>, mais sa forme masquée : le suivi des invitations
 * (EF-USER-007) sert déjà à corriger une adresse, écran par écran et
 * sous contrôle. Un export d'annuaire n'est pas nécessaire pour relancer
 * des comptes, et il circulerait par courriel et par clé USB.
 *
 * <p>« Dernière relance » est la date de création de l'invitation encore
 * en attente : une réémission révoque la précédente et en crée une
 * nouvelle (EF-USER-007), la ligne courante est donc bien la dernière
 * tentative.
 */
@Service
class PendingInvitationReportService {

    private static final int MAX_ROWS = 2000;
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private final AccountInvitationRepository invitationRepository;
    private final Clock clock;

    PendingInvitationReportService(AccountInvitationRepository invitationRepository, Clock clock) {
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<PendingInvitationRow> rows() {
        Instant now = clock.instant();
        List<PendingInvitationRow> rows = new ArrayList<>();
        for (AccountInvitation invitation
                : invitationRepository.findPendingActivations(PageRequest.of(0, MAX_ROWS))) {
            UserAccount account = invitation.getUser();
            boolean expired = invitation.getExpiresAt().isBefore(now);
            long days = Duration.between(invitation.getCreatedAt(), now).toDays();
            rows.add(new PendingInvitationRow(
                    invitation.getPublicId(), account.getPublicId(),
                    account.getFirstName(), account.getLastName(),
                    mask(account.getEmail()),
                    invitation.getCreatedAt(), invitation.getExpiresAt(), expired, days));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    TabularDocument document(ZoneId zone) {
        List<PendingInvitationRow> rows = rows();
        long expired = rows.stream().filter(PendingInvitationRow::expired).count();
        List<List<String>> body = new ArrayList<>(rows.size());
        for (PendingInvitationRow r : rows) {
            body.add(List.of(nz(r.lastName()), nz(r.firstName()), nz(r.maskedEmail()),
                    stamp(r.lastSentAt(), zone), stamp(r.expiresAt(), zone),
                    r.expired() ? "Expirée" : "En attente", Long.toString(r.daysPending())));
        }
        return new TabularDocument("Invitations non activées",
                "Situation au " + stamp(clock.instant(), zone),
                List.of(new TabularDocument.Fact("Comptes en attente", Integer.toString(rows.size())),
                        new TabularDocument.Fact("Dont invitations expirées", Long.toString(expired))),
                List.of("Nom", "Prénom", "Adresse (masquée)", "Dernière relance", "Expire le",
                        "État", "Jours d'attente"),
                body,
                List.of("L'adresse électronique n'apparaît que sous forme masquée : "
                                + "corrigez-la depuis l'écran de suivi des invitations.",
                        "« Dernière relance » est la date de l'invitation encore active : "
                                + "une réémission révoque la précédente."));
    }

    /**
     * Forme masquée d'une adresse ({@code c…e@e…c.test}), reprise de la
     * politique du journal de délivrabilité (docs/02 §11.3) : reconnaître
     * une adresse, sans la publier.
     */
    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "…";
        }
        return maskPart(email.substring(0, at)) + "@" + maskPart(email.substring(at + 1));
    }

    private static String maskPart(String part) {
        if (part.length() <= 2) {
            return part.charAt(0) + "…";
        }
        return part.charAt(0) + "…" + part.charAt(part.length() - 1);
    }

    private String stamp(Instant instant, ZoneId zone) {
        return instant == null ? "" : DAY.format(ZonedDateTime.ofInstant(instant, zone));
    }

    private String stamp(Instant instant) {
        return stamp(instant, clock.getZone());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param daysPending jours écoulés depuis la dernière relance —
     *                    l'indicateur qui décide de relancer ou d'appeler
     */
    record PendingInvitationRow(
            java.util.UUID invitationPublicId,
            java.util.UUID userPublicId,
            String firstName,
            String lastName,
            String maskedEmail,
            Instant lastSentAt,
            Instant expiresAt,
            boolean expired,
            long daysPending) {
    }
}
