package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicResourceType;
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
import java.util.Set;
import java.util.UUID;

/**
 * Référentiel des formations (docs/04-modele-donnees.md §12.2). CRUD,
 * archivage logique et restauration ; aucune suppression physique.
 * {@code code} immuable après création. L'archivage est refusé tant que
 * des niveaux ou des promotions actifs la référencent.
 */
@Service
@Transactional
class ProgramService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final ProgramRepository programRepository;
    private final ProgramLevelRepository programLevelRepository;
    private final PromotionRepository promotionRepository;
    private final AcademicChangePublisher changePublisher;

    ProgramService(ProgramRepository programRepository, ProgramLevelRepository programLevelRepository,
                   PromotionRepository promotionRepository, AcademicChangePublisher changePublisher) {
        this.programRepository = programRepository;
        this.programLevelRepository = programLevelRepository;
        this.promotionRepository = promotionRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<ProgramResponse> list(String statusFilter, String textFilter, int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Program>> specs = new ArrayList<>();
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add(AcademicSpecifications.hasStatus(status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AcademicSpecifications.matchesCodeOrName(text)));
        Page<Program> result = programRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, ProgramResponse::from);
    }

    @Transactional(readOnly = true)
    ProgramResponse get(UUID publicId) {
        return ProgramResponse.from(require(publicId));
    }

    ProgramResponse create(ProgramRequests.Create request, String callerSubject) {
        String code = request.code().trim();
        ProgramType type = parseType(request.programType());
        if (programRepository.existsByCode(code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        Program program = new Program(code, request.name().trim(), type,
                AcademicQuerySupport.trimToNull(request.description()));
        Long actorId = changePublisher.actorId(callerSubject);
        program.markCreatedBy(actorId);
        Program saved = programRepository.save(program);
        changePublisher.publish(AcademicResourceType.PROGRAM, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return ProgramResponse.from(saved);
    }

    ProgramResponse update(UUID publicId, ProgramRequests.Update request, String callerSubject) {
        Program program = require(publicId);
        if (program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        ProgramType type = parseType(request.programType());
        Long actorId = changePublisher.actorId(callerSubject);
        program.updateDetails(request.name().trim(), type,
                AcademicQuerySupport.trimToNull(request.description()), actorId);
        changePublisher.publish(AcademicResourceType.PROGRAM, program.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + program.getCode());
        return ProgramResponse.from(program);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        Program program = require(publicId);
        if (program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        boolean hasActiveChildren =
                programLevelRepository.existsByProgramIdAndStatus(program.getId(), AcademicStatus.ACTIVE)
                        || promotionRepository.existsByProgramIdAndStatus(program.getId(), AcademicStatus.ACTIVE);
        if (hasActiveChildren) {
            throw new AcademicException(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        program.archive(reason, actorId, Instant.now());
        changePublisher.publish(AcademicResourceType.PROGRAM, program.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, "code=" + program.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        Program program = require(publicId);
        if (!program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        program.restore(actorId);
        changePublisher.publish(AcademicResourceType.PROGRAM, program.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, "code=" + program.getCode());
    }

    private Program require(UUID publicId) {
        return programRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    private static ProgramType parseType(String value) {
        try {
            return ProgramType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new AcademicException(AcademicException.Kind.INVALID_PROGRAM_TYPE);
        }
    }
}
