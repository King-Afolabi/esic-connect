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
import java.util.Set;
import java.util.UUID;

/**
 * Référentiel des niveaux d'une formation (docs/04-modele-donnees.md
 * §12.3). Rattachés à une {@link Program}, {@code code} unique par
 * formation et immuable. Création interdite sous une formation archivée ;
 * archivage refusé tant qu'il reste des classes actives ; restauration
 * refusée sous une formation archivée.
 */
@Service
@Transactional
class ProgramLevelService {

    private static final Set<String> SORTABLE =
            Set.of("code", "name", "sequenceNumber", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "sequenceNumber");

    private final ProgramLevelRepository programLevelRepository;
    private final ProgramRepository programRepository;
    private final ClassGroupRepository classGroupRepository;
    private final AcademicScopeGuard scopeGuard;
    private final AcademicChangePublisher changePublisher;

    ProgramLevelService(ProgramLevelRepository programLevelRepository, ProgramRepository programRepository,
                        ClassGroupRepository classGroupRepository, AcademicScopeGuard scopeGuard,
                        AcademicChangePublisher changePublisher) {
        this.programLevelRepository = programLevelRepository;
        this.programRepository = programRepository;
        this.classGroupRepository = classGroupRepository;
        this.scopeGuard = scopeGuard;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<ProgramLevelResponse> listForProgram(UUID programPublicId, String statusFilter, String textFilter,
                                                      int page, int size, String sort) {
        Program program = requireProgram(programPublicId);
        scopeGuard.requireProgramInScope(program);
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<ProgramLevel>> specs = new ArrayList<>();
        specs.add(AcademicSpecifications.levelHasProgram(program.getId()));
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add(AcademicSpecifications.hasStatus(status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add(AcademicSpecifications.matchesCodeOrName(text)));
        Page<ProgramLevel> result = programLevelRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, ProgramLevelResponse::from);
    }

    @Transactional(readOnly = true)
    ProgramLevelResponse get(UUID publicId) {
        ProgramLevel level = require(publicId);
        scopeGuard.requireProgramInScope(level.getProgram());
        return ProgramLevelResponse.from(level);
    }

    ProgramLevelResponse create(UUID programPublicId, ProgramLevelRequests.Create request, String callerSubject) {
        Program program = requireProgram(programPublicId);
        scopeGuard.requireProgramInScope(program);
        if (program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        String code = request.code().trim();
        if (programLevelRepository.existsByProgramIdAndCode(program.getId(), code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        ProgramLevel level = new ProgramLevel(program, code, request.name().trim(), request.sequenceNumber());
        Long actorId = changePublisher.actorId(callerSubject);
        level.markCreatedBy(actorId);
        ProgramLevel saved = programLevelRepository.save(level);
        changePublisher.publish(AcademicResourceType.PROGRAM_LEVEL, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return ProgramLevelResponse.from(saved);
    }

    ProgramLevelResponse update(UUID publicId, ProgramLevelRequests.Update request, String callerSubject) {
        ProgramLevel level = require(publicId);
        scopeGuard.requireProgramInScope(level.getProgram());
        if (level.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        level.updateDetails(request.name().trim(), request.sequenceNumber(), actorId);
        changePublisher.publish(AcademicResourceType.PROGRAM_LEVEL, level.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + level.getCode());
        return ProgramLevelResponse.from(level);
    }

    void archive(UUID publicId, String reason, String callerSubject) {
        ProgramLevel level = require(publicId);
        scopeGuard.requireProgramInScope(level.getProgram());
        if (level.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        if (classGroupRepository.existsByProgramLevelIdAndStatus(level.getId(), AcademicStatus.ACTIVE)) {
            throw new AcademicException(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        level.archive(reason, actorId, Instant.now());
        changePublisher.publish(AcademicResourceType.PROGRAM_LEVEL, level.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, "code=" + level.getCode());
    }

    void restore(UUID publicId, String callerSubject) {
        ProgramLevel level = require(publicId);
        scopeGuard.requireProgramInScope(level.getProgram());
        if (!level.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        if (level.getProgram().isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        level.restore(actorId);
        changePublisher.publish(AcademicResourceType.PROGRAM_LEVEL, level.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, "code=" + level.getCode());
    }

    private Program requireProgram(UUID publicId) {
        return programRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    private ProgramLevel require(UUID publicId) {
        return programLevelRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_LEVEL_NOT_FOUND));
    }
}
