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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Référentiel des promotions (docs/04-modele-donnees.md §12.4). Rattachées
 * à une formation et à une année scolaire (rattachements immuables),
 * {@code code} unique pour ce couple et immuable. Création interdite sous
 * une formation ou une année archivée ; la période, si renseignée, doit
 * être incluse dans celle de l'année scolaire. Archivage refusé tant
 * qu'il reste des classes actives ; restauration refusée sous un parent
 * archivé.
 */
@Service
@Transactional
class PromotionService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final PromotionRepository promotionRepository;
    private final ProgramRepository programRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ClassGroupRepository classGroupRepository;
    private final AcademicScopeGuard scopeGuard;
    private final AcademicChangePublisher changePublisher;

    PromotionService(PromotionRepository promotionRepository, ProgramRepository programRepository,
                     AcademicYearRepository academicYearRepository, ClassGroupRepository classGroupRepository,
                     AcademicScopeGuard scopeGuard, AcademicChangePublisher changePublisher) {
        this.promotionRepository = promotionRepository;
        this.programRepository = programRepository;
        this.academicYearRepository = academicYearRepository;
        this.classGroupRepository = classGroupRepository;
        this.scopeGuard = scopeGuard;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<PromotionResponse> list(String programPublicId, String academicYearPublicId, String statusFilter,
                                         String textFilter, int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        Set<Long> visible = scopeGuard.visibleProgramIds();
        if (visible != null && visible.isEmpty()) {
            return PageResponse.of(Page.<Promotion>empty(pageable), PromotionResponse::from);
        }
        List<Specification<Promotion>> specs = new ArrayList<>();
        if (visible != null) {
            specs.add(AcademicSpecifications.promotionProgramIdIn(visible));
        }
        if (programPublicId != null && !programPublicId.isBlank()) {
            specs.add(AcademicSpecifications.promotionHasProgram(requireProgram(parseUuid(programPublicId,
                    AcademicException.Kind.PROGRAM_NOT_FOUND)).getId()));
        }
        if (academicYearPublicId != null && !academicYearPublicId.isBlank()) {
            specs.add(AcademicSpecifications.promotionHasAcademicYear(requireYear(parseUuid(academicYearPublicId,
                    AcademicException.Kind.ACADEMIC_YEAR_NOT_FOUND)).getId()));
        }
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add(AcademicSpecifications.hasStatus(status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AcademicSpecifications.matchesCodeOrName(text)));
        Page<Promotion> result = promotionRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, PromotionResponse::from);
    }

    @Transactional(readOnly = true)
    PromotionResponse get(UUID publicId) {
        Promotion promotion = require(publicId);
        scopeGuard.requireProgramInScope(promotion.getProgram());
        return PromotionResponse.from(promotion);
    }

    PromotionResponse create(PromotionRequests.Create request, String callerSubject) {
        Program program = requireProgram(parseUuid(request.programPublicId(),
                AcademicException.Kind.PROGRAM_NOT_FOUND));
        scopeGuard.requireProgramInScope(program);
        if (program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        AcademicYear year = requireYear(parseUuid(request.academicYearPublicId(),
                AcademicException.Kind.ACADEMIC_YEAR_NOT_FOUND));
        if (year.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        requirePeriodWithinYear(request.startDate(), request.endDate(), year);
        String code = request.code().trim();
        if (promotionRepository.existsByProgramIdAndAcademicYearIdAndCode(program.getId(), year.getId(), code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        Promotion promotion = new Promotion(program, year, code, request.name().trim(),
                request.startDate(), request.endDate());
        Long actorId = changePublisher.actorId(callerSubject);
        promotion.markCreatedBy(actorId);
        Promotion saved = promotionRepository.save(promotion);
        changePublisher.publish(AcademicResourceType.PROMOTION, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return PromotionResponse.from(saved);
    }

    PromotionResponse update(UUID publicId, PromotionRequests.Update request, String callerSubject) {
        Promotion promotion = require(publicId);
        scopeGuard.requireProgramInScope(promotion.getProgram());
        if (promotion.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        requirePeriodWithinYear(request.startDate(), request.endDate(), promotion.getAcademicYear());
        Long actorId = changePublisher.actorId(callerSubject);
        promotion.updateDetails(request.name().trim(), request.startDate(), request.endDate(), actorId);
        changePublisher.publish(AcademicResourceType.PROMOTION, promotion.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + promotion.getCode());
        return PromotionResponse.from(promotion);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        Promotion promotion = require(publicId);
        scopeGuard.requireProgramInScope(promotion.getProgram());
        if (promotion.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        if (classGroupRepository.existsByPromotionIdAndStatus(promotion.getId(), AcademicStatus.ACTIVE)) {
            throw new AcademicException(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        promotion.archive(reason, actorId, Instant.now());
        changePublisher.publish(AcademicResourceType.PROMOTION, promotion.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, "code=" + promotion.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        Promotion promotion = require(publicId);
        scopeGuard.requireProgramInScope(promotion.getProgram());
        if (!promotion.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        if (promotion.getProgram().isArchived() || promotion.getAcademicYear().isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        promotion.restore(actorId);
        changePublisher.publish(AcademicResourceType.PROMOTION, promotion.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, "code=" + promotion.getCode());
    }

    private Promotion require(UUID publicId) {
        return promotionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROMOTION_NOT_FOUND));
    }

    private Program requireProgram(UUID publicId) {
        return programRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    private AcademicYear requireYear(UUID publicId) {
        return academicYearRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.ACADEMIC_YEAR_NOT_FOUND));
    }

    /**
     * Valide la période de la promotion : cohérente en elle-même et, si
     * renseignée, strictement incluse dans celle de l'année scolaire.
     */
    private static void requirePeriodWithinYear(LocalDate start, LocalDate end, AcademicYear year) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new AcademicException(AcademicException.Kind.INVALID_PERIOD);
        }
        if (start.isBefore(year.getStartDate()) || end.isAfter(year.getEndDate())) {
            throw new AcademicException(AcademicException.Kind.PROMOTION_PERIOD_OUT_OF_YEAR);
        }
    }

    private static UUID parseUuid(String value, AcademicException.Kind notFound) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new AcademicException(notFound);
        }
    }
}
