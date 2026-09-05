package com.esic.connect.audit.internal;

import com.esic.connect.document.TabularDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Consultation et export de la piste d'audit (EF-AUD-002 ;
 * docs/02 §23.4).
 *
 * <p><strong>Lecture seule et immuable.</strong> Ce service n'offre ni
 * modification ni suppression : « l'audit […] ne peut être modifié ni
 * effacé ». Il n'existe donc aucune route d'écriture — l'unique auteur
 * de la table reste le gestionnaire d'outbox.
 *
 * <p><strong>Ce qui n'est pas exposé.</strong> Les colonnes JSON
 * ({@code old_values_json}, {@code new_values_json},
 * {@code metadata_json}) ne sortent pas de la base. Elles contiennent
 * l'avant/après d'opérations métier, dont la lisibilité dépend du
 * module qui les a écrites, et les rendre telles quelles reviendrait à
 * déverser du contenu non gouverné dans un écran et dans un export.
 * L'écran affiche <em>qui, quoi, sur quoi, quand, avec quel résultat et
 * pour quel motif</em> — ce que le cahier exige (§23.2).
 */
@Service
class AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    /** Un export d'audit reste borné : il n'a pas à devenir un dump. */
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRENCH);

    private final AuditEventRepository repository;

    AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    Page<AuditResponses.AuditRow> search(AuditQuery query, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repository.findAll(specification(query),
                        PageRequest.of(Math.max(0, page), boundedSize,
                                Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(AuditQueryService::toRow);
    }

    @Transactional(readOnly = true)
    TabularDocument export(AuditQuery query, ZoneId zone) {
        List<AuditEvent> events = repository.findAll(specification(query),
                        PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent();
        List<List<String>> rows = new ArrayList<>(events.size());
        for (AuditEvent event : events) {
            rows.add(List.of(
                    event.getOccurredAt() == null ? ""
                            : STAMP.format(ZonedDateTime.ofInstant(event.getOccurredAt(), zone)),
                    nz(event.getActorDisplaySnapshot()),
                    nz(event.getActorRole()),
                    nz(event.getAction()),
                    nz(event.getCategory()),
                    nz(event.getResourceType()),
                    event.getResourcePublicId() == null ? "" : event.getResourcePublicId().toString(),
                    nz(event.getResult()),
                    nz(event.getReason()),
                    event.getCorrelationId() == null ? "" : event.getCorrelationId().toString()));
        }
        List<String> notes = new ArrayList<>();
        notes.add("Piste d'audit — lecture seule. Aucune trace ne peut être modifiée ni effacée.");
        notes.add("Aucune adresse IP ni donnée personnelle superflue n'est conservée dans l'audit métier.");
        if (events.size() >= MAX_EXPORT_ROWS) {
            // Dire que l'export est tronqué : un export silencieusement
            // amputé se lit comme une absence d'événements.
            notes.add("Export tronqué à " + MAX_EXPORT_ROWS
                    + " lignes. Affinez la période ou les filtres pour obtenir la suite.");
        }
        return new TabularDocument("Piste d'audit", periodLabel(query, zone),
                List.of(new TabularDocument.Fact("Événements exportés", Integer.toString(events.size()))),
                List.of("Date", "Acteur", "Rôle", "Action", "Catégorie", "Ressource",
                        "Identifiant de ressource", "Résultat", "Motif", "Corrélation"),
                rows, notes);
    }

    /** Valeurs distinctes présentes, pour alimenter les filtres de l'écran. */
    @Transactional(readOnly = true)
    AuditResponses.AuditFacets facets() {
        return new AuditResponses.AuditFacets(
                repository.distinctActions(), repository.distinctCategories(),
                repository.distinctResourceTypes(), repository.distinctResults());
    }

    private static Specification<AuditEvent> specification(AuditQuery query) {
        List<Specification<AuditEvent>> specs = new ArrayList<>();
        if (query.from() != null) {
            specs.add(AuditEventSpecifications.occurredFrom(query.from()));
        }
        if (query.to() != null) {
            specs.add(AuditEventSpecifications.occurredUntil(query.to()));
        }
        if (notBlank(query.action())) {
            specs.add(AuditEventSpecifications.hasAction(query.action()));
        }
        if (notBlank(query.category())) {
            specs.add(AuditEventSpecifications.hasCategory(query.category()));
        }
        if (notBlank(query.resourceType())) {
            specs.add(AuditEventSpecifications.hasResourceType(query.resourceType()));
        }
        if (notBlank(query.result())) {
            specs.add(AuditEventSpecifications.hasResult(query.result()));
        }
        if (query.actorPublicId() != null) {
            specs.add(AuditEventSpecifications.hasActor(query.actorPublicId()));
        }
        if (query.resourcePublicId() != null) {
            specs.add(AuditEventSpecifications.hasResource(query.resourcePublicId()));
        }
        if (query.correlationId() != null) {
            specs.add(AuditEventSpecifications.hasCorrelation(query.correlationId()));
        }
        return specs.isEmpty() ? Specification.unrestricted() : Specification.allOf(specs);
    }

    private static AuditResponses.AuditRow toRow(AuditEvent event) {
        return new AuditResponses.AuditRow(
                event.getPublicId(), event.getOccurredAt(), event.getActorDisplaySnapshot(),
                event.getActorPublicIdSnapshot(), event.getActorRole(), event.getAction(),
                event.getCategory(), event.getResourceType(), event.getResourcePublicId(),
                event.getResult(), event.getReason(), event.getCorrelationId());
    }

    private static String periodLabel(AuditQuery query, ZoneId zone) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
        String start = query.from() == null ? "origine"
                : day.format(ZonedDateTime.ofInstant(query.from(), zone));
        String end = query.to() == null ? "aujourd'hui"
                : day.format(ZonedDateTime.ofInstant(query.to(), zone));
        return "Du " + start + " au " + end;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Filtres de consultation, tous facultatifs. */
    record AuditQuery(
            Instant from,
            Instant to,
            String action,
            String category,
            String resourceType,
            String result,
            UUID actorPublicId,
            UUID resourcePublicId,
            UUID correlationId) {
    }
}
