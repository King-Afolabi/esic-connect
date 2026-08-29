package com.esic.connect.alternation.internal;

import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Modèles réutilisables de rythme (docs/04 §14.1). CRUD, archivage
 * logique et restauration ; aucune suppression physique. {@code code}
 * et {@code type} immuables après création. La configuration est validée
 * et normalisée par {@link AlternationConfigParser} à chaque écriture —
 * aucun JSON incohérent n'est stocké.
 */
@Service
@Transactional
class WorkStudyPatternService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final WorkStudyPatternRepository patternRepository;
    private final AlternationConfigParser configParser;
    private final AlternationChangePublisher changePublisher;
    private final ObjectMapper objectMapper;

    WorkStudyPatternService(WorkStudyPatternRepository patternRepository,
                            AlternationConfigParser configParser,
                            AlternationChangePublisher changePublisher,
                            ObjectMapper objectMapper) {
        this.patternRepository = patternRepository;
        this.configParser = configParser;
        this.changePublisher = changePublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    PageResponse<WorkStudyPatternResponse> list(String statusFilter, String typeFilter, String textFilter,
                                                int page, int size, String sort) {
        Pageable pageable = AlternationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<WorkStudyPattern>> specs = new ArrayList<>();
        parseStatus(statusFilter).ifPresent(status -> specs.add(AlternationSpecifications.patternHasStatus(status)));
        parseType(typeFilter).ifPresent(type -> specs.add(AlternationSpecifications.patternHasType(type)));
        AlternationQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AlternationSpecifications.patternMatchesCodeOrName(text)));
        Page<WorkStudyPattern> result = patternRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    WorkStudyPatternResponse get(UUID publicId) {
        return toResponse(require(publicId));
    }

    WorkStudyPatternResponse create(WorkStudyPatternRequests.Create request, String callerSubject) {
        String code = request.code().trim();
        WorkStudyPatternType type = requireType(request.type());
        if (patternRepository.existsByCode(code)) {
            throw new AlternationException(AlternationException.Kind.DUPLICATE_CODE);
        }
        AlternationConfigParser.ParsedConfiguration parsed = configParser.parse(type, request.cycleLengthWeeks(),
                jsonToString(request.configuration()));
        String canonical = configParser.canonicalize(parsed);

        Long actorId = changePublisher.actorId(callerSubject);
        WorkStudyPattern pattern = new WorkStudyPattern(code, request.name().trim(),
                AlternationQuerySupport.trimToNull(request.description()), type,
                parsed.normalizedCycleLengthWeeks(), canonical);
        pattern.markCreatedBy(actorId);
        WorkStudyPattern saved = patternRepository.save(pattern);
        changePublisher.publish(AlternationResourceType.WORK_STUDY_PATTERN, saved.getPublicId(),
                AlternationChangeAction.CREATED, actorId, detail(saved));
        return toResponse(saved);
    }

    WorkStudyPatternResponse update(UUID publicId, WorkStudyPatternRequests.Update request, String callerSubject) {
        WorkStudyPattern pattern = require(publicId);
        if (pattern.isArchived()) {
            throw new AlternationException(AlternationException.Kind.INVALID_STATE);
        }
        AlternationConfigParser.ParsedConfiguration parsed = configParser.parse(pattern.getPatternType(),
                request.cycleLengthWeeks(), jsonToString(request.configuration()));
        String canonical = configParser.canonicalize(parsed);

        Long actorId = changePublisher.actorId(callerSubject);
        pattern.updateDetails(request.name().trim(), AlternationQuerySupport.trimToNull(request.description()),
                parsed.normalizedCycleLengthWeeks(), canonical, actorId);
        changePublisher.publish(AlternationResourceType.WORK_STUDY_PATTERN, pattern.getPublicId(),
                AlternationChangeAction.UPDATED, actorId, detail(pattern));
        return toResponse(pattern);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        WorkStudyPattern pattern = require(publicId);
        if (pattern.isArchived()) {
            throw new AlternationException(AlternationException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        pattern.archive(reason, actorId, Instant.now());
        changePublisher.publish(AlternationResourceType.WORK_STUDY_PATTERN, pattern.getPublicId(),
                AlternationChangeAction.ARCHIVED, actorId, detail(pattern));
    }

    void restore(UUID publicId, String callerSubject) {
        WorkStudyPattern pattern = require(publicId);
        if (!pattern.isArchived()) {
            throw new AlternationException(AlternationException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        pattern.restore(actorId);
        changePublisher.publish(AlternationResourceType.WORK_STUDY_PATTERN, pattern.getPublicId(),
                AlternationChangeAction.RESTORED, actorId, detail(pattern));
    }

    // ------------------------------------------------------------------

    WorkStudyPattern require(UUID publicId) {
        return patternRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.PATTERN_NOT_FOUND));
    }

    private WorkStudyPatternResponse toResponse(WorkStudyPattern pattern) {
        return WorkStudyPatternResponse.from(pattern, readConfiguration(pattern.getConfigurationJson()));
    }

    private JsonNode readConfiguration(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception unreadable) {
            // La configuration a été validée et canonicalisée à l'écriture :
            // un échec ici est un incident interne, jamais une entrée client.
            throw new AlternationException(AlternationException.Kind.INVALID_CONFIGURATION, "relecture impossible");
        }
    }

    private String jsonToString(JsonNode node) {
        return node == null ? null : node.toString();
    }

    private static String detail(WorkStudyPattern pattern) {
        return "code=" + pattern.getCode() + ";type=" + pattern.getPatternType().name();
    }

    private static WorkStudyPatternType requireType(String value) {
        try {
            return WorkStudyPatternType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_PATTERN_TYPE);
        }
    }

    private static Optional<WorkStudyPatternType> parseType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(WorkStudyPatternType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_FILTER);
        }
    }

    private static Optional<WorkStudyPatternStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(WorkStudyPatternStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_FILTER);
        }
    }
}
