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
 * Référentiel des années scolaires (docs/04-modele-donnees.md §12.1).
 * CRUD, archivage logique et restauration ; aucune suppression physique.
 * {@code code} immuable après création. Période validée
 * ({@code end_date > start_date}). L'archivage est refusé tant que des
 * promotions actives la référencent.
 */
@Service
@Transactional
class AcademicYearService {

    private static final Set<String> SORTABLE =
            Set.of("code", "name", "startDate", "endDate", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final AcademicYearRepository academicYearRepository;
    private final PromotionRepository promotionRepository;
    private final AcademicChangePublisher changePublisher;

    AcademicYearService(AcademicYearRepository academicYearRepository,
                        PromotionRepository promotionRepository,
                        AcademicChangePublisher changePublisher) {
        this.academicYearRepository = academicYearRepository;
        this.promotionRepository = promotionRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<AcademicYearResponse> list(String statusFilter, String textFilter, int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<AcademicYear>> specs = new ArrayList<>();
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add(AcademicSpecifications.hasStatus(status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AcademicSpecifications.matchesCodeOrName(text)));
        Page<AcademicYear> result = academicYearRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, AcademicYearResponse::from);
    }

    @Transactional(readOnly = true)
    AcademicYearResponse get(UUID publicId) {
        return AcademicYearResponse.from(require(publicId));
    }

    AcademicYearResponse create(AcademicYearRequests.Create request, String callerSubject) {
        String code = request.code().trim();
        requireValidPeriod(request.startDate(), request.endDate());
        if (academicYearRepository.existsByCode(code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        AcademicYear year = new AcademicYear(code, request.name().trim(), request.startDate(), request.endDate());
        Long actorId = changePublisher.actorId(callerSubject);
        year.markCreatedBy(actorId);
        AcademicYear saved = academicYearRepository.save(year);
        changePublisher.publish(AcademicResourceType.ACADEMIC_YEAR, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return AcademicYearResponse.from(saved);
    }

    AcademicYearResponse update(UUID publicId, AcademicYearRequests.Update request, String callerSubject) {
        AcademicYear year = require(publicId);
        if (year.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        requireValidPeriod(request.startDate(), request.endDate());
        // Une promotion existante dont la période est renseignée ne doit
        // pas se retrouver hors de la nouvelle période de l'année. Test
        // ciblé (deux `exists`), sans charger la liste des promotions.
        if (promotionRepository.existsByAcademicYearIdAndStartDateBefore(year.getId(), request.startDate())
                || promotionRepository.existsByAcademicYearIdAndEndDateAfter(year.getId(), request.endDate())) {
            throw new AcademicException(AcademicException.Kind.ACADEMIC_YEAR_PERIOD_CONFLICT);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        year.updateDetails(request.name().trim(), request.startDate(), request.endDate(), actorId);
        changePublisher.publish(AcademicResourceType.ACADEMIC_YEAR, year.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + year.getCode());
        return AcademicYearResponse.from(year);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        AcademicYear year = require(publicId);
        if (year.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        if (promotionRepository.existsByAcademicYearIdAndStatus(year.getId(), AcademicStatus.ACTIVE)) {
            throw new AcademicException(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        year.archive(reason, actorId, Instant.now());
        changePublisher.publish(AcademicResourceType.ACADEMIC_YEAR, year.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, "code=" + year.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        AcademicYear year = require(publicId);
        if (!year.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        year.restore(actorId);
        changePublisher.publish(AcademicResourceType.ACADEMIC_YEAR, year.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, "code=" + year.getCode());
    }

    private AcademicYear require(UUID publicId) {
        return academicYearRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.ACADEMIC_YEAR_NOT_FOUND));
    }

    private static void requireValidPeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new AcademicException(AcademicException.Kind.INVALID_PERIOD);
        }
    }
}
