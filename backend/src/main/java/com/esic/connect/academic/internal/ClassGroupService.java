package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicResourceType;
import com.esic.connect.organization.SiteDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Référentiel des classes / groupes (docs/04-modele-donnees.md §12.5).
 * Rattachées à une promotion, à un niveau de la même formation que la
 * promotion, et à un site. Le site n'est qu'une valeur technique
 * ({@code site_id}) résolue via le port {@link SiteDirectory} : aucune
 * entité JPA partagée avec le module {@code organization} (décision D4).
 *
 * <p>{@code code} unique dans la promotion et immuable ; rattachements
 * immuables. Création interdite sous un parent archivé ; restauration
 * refusée sous un parent archivé. Aucune suppression physique.
 */
@Service
@Transactional
class ClassGroupService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final ClassGroupRepository classGroupRepository;
    private final PromotionRepository promotionRepository;
    private final ProgramLevelRepository programLevelRepository;
    private final SiteDirectory siteDirectory;
    private final AcademicChangePublisher changePublisher;

    ClassGroupService(ClassGroupRepository classGroupRepository, PromotionRepository promotionRepository,
                      ProgramLevelRepository programLevelRepository, SiteDirectory siteDirectory,
                      AcademicChangePublisher changePublisher) {
        this.classGroupRepository = classGroupRepository;
        this.promotionRepository = promotionRepository;
        this.programLevelRepository = programLevelRepository;
        this.siteDirectory = siteDirectory;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<ClassGroupResponse> list(String promotionPublicId, String programLevelPublicId, String sitePublicId,
                                          String statusFilter, String textFilter, int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<ClassGroup>> specs = new ArrayList<>();
        if (promotionPublicId != null && !promotionPublicId.isBlank()) {
            specs.add(AcademicSpecifications.classGroupHasPromotion(requirePromotion(parseUuid(promotionPublicId,
                    AcademicException.Kind.PROMOTION_NOT_FOUND)).getId()));
        }
        if (programLevelPublicId != null && !programLevelPublicId.isBlank()) {
            specs.add(AcademicSpecifications.classGroupHasProgramLevel(requireLevel(parseUuid(programLevelPublicId,
                    AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND)).getId()));
        }
        if (sitePublicId != null && !sitePublicId.isBlank()) {
            SiteDirectory.SiteRef site = siteDirectory.findByPublicId(parseUuid(sitePublicId,
                            AcademicException.Kind.SITE_NOT_FOUND))
                    .orElseThrow(() -> new AcademicException(AcademicException.Kind.SITE_NOT_FOUND));
            specs.add(AcademicSpecifications.classGroupHasSite(site.internalId()));
        }
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add(AcademicSpecifications.hasStatus(status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AcademicSpecifications.matchesCodeOrName(text)));
        Page<ClassGroup> result = classGroupRepository.findAll(Specification.allOf(specs), pageable);

        Map<Long, UUID> sitePublicIds = new HashMap<>();
        return PageResponse.of(result, cg -> ClassGroupResponse.from(cg,
                sitePublicIds.computeIfAbsent(cg.getSiteId(), this::resolveSitePublicId)));
    }

    @Transactional(readOnly = true)
    ClassGroupResponse get(UUID publicId) {
        ClassGroup classGroup = require(publicId);
        return ClassGroupResponse.from(classGroup, resolveSitePublicId(classGroup.getSiteId()));
    }

    ClassGroupResponse create(ClassGroupRequests.Create request, String callerSubject) {
        Promotion promotion = requirePromotion(parseUuid(request.promotionPublicId(),
                AcademicException.Kind.PROMOTION_NOT_FOUND));
        if (promotion.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        ProgramLevel level = requireLevel(parseUuid(request.programLevelPublicId(),
                AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND));
        if (level.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        // Le niveau doit appartenir à la formation de la promotion (ajustement demandé).
        if (!level.getProgram().getId().equals(promotion.getProgram().getId())) {
            throw new AcademicException(AcademicException.Kind.PROGRAM_LEVEL_MISMATCH);
        }
        SiteDirectory.SiteRef site = siteDirectory.findByPublicId(parseUuid(request.sitePublicId(),
                        AcademicException.Kind.SITE_NOT_FOUND))
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.SITE_NOT_FOUND));
        if (site.archived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        String code = request.code().trim();
        if (classGroupRepository.existsByPromotionIdAndCode(promotion.getId(), code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        ClassGroup classGroup = new ClassGroup(promotion, level, site.internalId(), code,
                request.name().trim(), request.capacity());
        Long actorId = changePublisher.actorId(callerSubject);
        classGroup.markCreatedBy(actorId);
        ClassGroup saved = classGroupRepository.save(classGroup);
        changePublisher.publish(AcademicResourceType.CLASS_GROUP, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return ClassGroupResponse.from(saved, site.publicId());
    }

    ClassGroupResponse update(UUID publicId, ClassGroupRequests.Update request, String callerSubject) {
        ClassGroup classGroup = require(publicId);
        if (classGroup.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        classGroup.updateDetails(request.name().trim(), request.capacity(), actorId);
        changePublisher.publish(AcademicResourceType.CLASS_GROUP, classGroup.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + classGroup.getCode());
        return ClassGroupResponse.from(classGroup, resolveSitePublicId(classGroup.getSiteId()));
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        ClassGroup classGroup = require(publicId);
        if (classGroup.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        classGroup.archive(reason, actorId, Instant.now());
        changePublisher.publish(AcademicResourceType.CLASS_GROUP, classGroup.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, "code=" + classGroup.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        ClassGroup classGroup = require(publicId);
        if (!classGroup.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Promotion promotion = classGroup.getPromotion();
        ProgramLevel level = classGroup.getProgramLevel();
        // Aucun maillon de la chaîne de rattachement ne doit être archivé :
        // promotion, sa formation, son année scolaire, le niveau et la
        // formation du niveau.
        if (promotion.isArchived()
                || promotion.getProgram().isArchived()
                || promotion.getAcademicYear().isArchived()
                || level.isArchived()
                || level.getProgram().isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        // Le site doit toujours exister et être actif.
        SiteDirectory.SiteRef site = siteDirectory.findByInternalId(classGroup.getSiteId())
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.SITE_NOT_FOUND));
        if (site.archived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        // Revérifie l'invariant posé à la création (le niveau appartient à
        // la formation de la promotion) : il pourrait avoir été rompu par
        // une opération concurrente sur un parent.
        if (!level.getProgram().getId().equals(promotion.getProgram().getId())) {
            throw new AcademicException(AcademicException.Kind.PROGRAM_LEVEL_MISMATCH);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        classGroup.restore(actorId);
        changePublisher.publish(AcademicResourceType.CLASS_GROUP, classGroup.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, "code=" + classGroup.getCode());
    }

    /**
     * Résout l'identifiant public du site rattaché. La clé étrangère SQL
     * garantit que le site existe ; en cas d'incohérence de données, on
     * lève une erreur métier contrôlée plutôt que de renvoyer {@code null}
     * silencieusement — sans jamais exposer l'identifiant SQL interne.
     */
    private UUID resolveSitePublicId(Long siteInternalId) {
        return siteDirectory.findByInternalId(siteInternalId)
                .map(SiteDirectory.SiteRef::publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.SITE_NOT_FOUND));
    }

    private ClassGroup require(UUID publicId) {
        return classGroupRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.CLASS_GROUP_NOT_FOUND));
    }

    private Promotion requirePromotion(UUID publicId) {
        return promotionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROMOTION_NOT_FOUND));
    }

    private ProgramLevel requireLevel(UUID publicId) {
        return programLevelRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND));
    }

    private static UUID parseUuid(String value, AcademicException.Kind notFound) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new AcademicException(notFound);
        }
    }
}
