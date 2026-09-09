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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Référentiel des matières (EF-ACA-006, docs/02 §6.4).
 *
 * <p>CRUD, archivage logique et restauration ; aucune suppression
 * physique. Le code est immuable après création : il sert de référence
 * dans les fichiers de planning importés, le changer romprait
 * silencieusement des imports déjà écrits.
 *
 * <p><strong>Périmètre.</strong> Une matière peut être transverse à
 * plusieurs formations. Le rattachement est donc contrôlé formation par
 * formation via {@link AcademicScopeGuard} : un responsable pédagogique
 * ne peut rattacher une matière qu'à ses propres formations, mais voit
 * l'ensemble du catalogue — une matière n'est pas une donnée sensible, et
 * masquer les matières d'autrui empêcherait d'en réutiliser une au lieu
 * d'en créer un doublon.
 */
@Service
@Transactional
class SubjectService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final SubjectRepository subjectRepository;
    private final SubjectProgramRepository subjectProgramRepository;
    private final ProgramRepository programRepository;
    private final AcademicScopeGuard scopeGuard;
    private final AcademicChangePublisher changePublisher;

    SubjectService(SubjectRepository subjectRepository,
                   SubjectProgramRepository subjectProgramRepository,
                   ProgramRepository programRepository,
                   AcademicScopeGuard scopeGuard,
                   AcademicChangePublisher changePublisher) {
        this.subjectRepository = subjectRepository;
        this.subjectProgramRepository = subjectProgramRepository;
        this.programRepository = programRepository;
        this.scopeGuard = scopeGuard;
        this.changePublisher = changePublisher;
    }

    @Transactional(readOnly = true)
    PageResponse<SubjectResponse> list(String statusFilter, String textFilter, String programPublicId,
                                       int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Subject>> specs = new ArrayList<>();
        AcademicQuerySupport.parseStatus(statusFilter)
                .ifPresent(status -> specs.add((root, query, cb) ->
                        cb.equal(root.get("status"), status)));
        AcademicQuerySupport.normalizeText(textFilter)
                .ifPresent(text -> specs.add((root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("code")), "%" + text.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("name")), "%" + text.toLowerCase() + "%"))));
        if (programPublicId != null && !programPublicId.isBlank()) {
            Program program = requireProgram(programPublicId);
            Set<Long> subjectIds = subjectProgramRepository.findByProgramId(program.getId()).stream()
                    .map(SubjectProgram::getSubjectId)
                    .collect(Collectors.toSet());
            if (subjectIds.isEmpty()) {
                return PageResponse.of(Page.<Subject>empty(pageable), this::toResponse);
            }
            specs.add((root, query, cb) -> root.get("id").in(subjectIds));
        }
        Page<Subject> result = subjectRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, toResponseWith(programsOf(result.getContent())));
    }

    @Transactional(readOnly = true)
    SubjectResponse get(UUID publicId) {
        return toResponse(require(publicId));
    }

    SubjectResponse create(SubjectRequests.Create request, String callerSubject) {
        String code = request.code().trim();
        if (subjectRepository.existsByCode(code)) {
            throw new AcademicException(AcademicException.Kind.DUPLICATE_CODE);
        }
        Subject subject = new Subject(code, request.name().trim(),
                AcademicQuerySupport.trimToNull(request.description()), request.hourlyVolume());
        Long actorId = changePublisher.actorId(callerSubject);
        subject.markCreatedBy(actorId);
        Subject saved = subjectRepository.save(subject);
        replacePrograms(saved, request.programPublicIds());
        changePublisher.publish(AcademicResourceType.SUBJECT, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, "code=" + code);
        return toResponse(saved);
    }

    SubjectResponse update(UUID publicId, SubjectRequests.Update request, String callerSubject) {
        Subject subject = require(publicId);
        if (subject.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ENTITY_ARCHIVED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        subject.updateDetails(request.name().trim(),
                AcademicQuerySupport.trimToNull(request.description()), request.hourlyVolume(), actorId);
        replacePrograms(subject, request.programPublicIds());
        changePublisher.publish(AcademicResourceType.SUBJECT, subject.getPublicId(),
                AcademicChangeAction.UPDATED, actorId, "code=" + subject.getCode());
        return toResponse(subject);
    }

    SubjectResponse archive(UUID publicId, String reason, String callerSubject) {
        Subject subject = require(publicId);
        if (subject.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        subject.archive(Instant.now(), actorId);
        changePublisher.publish(AcademicResourceType.SUBJECT, subject.getPublicId(),
                AcademicChangeAction.ARCHIVED, actorId, reason);
        return toResponse(subject);
    }

    SubjectResponse restore(UUID publicId, String callerSubject) {
        Subject subject = require(publicId);
        if (!subject.isArchived()) {
            throw new AcademicException(AcademicException.Kind.INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        subject.restore(actorId);
        changePublisher.publish(AcademicResourceType.SUBJECT, subject.getPublicId(),
                AcademicChangeAction.RESTORED, actorId, null);
        return toResponse(subject);
    }

    // ------------------------------------------------------------------

    /**
     * Remplace la liste des formations rattachées. {@code null} laisse la
     * liste inchangée — un client qui ne s'intéresse pas au rattachement
     * ne doit pas l'effacer par omission ; une liste vide la vide bien.
     */
    private void replacePrograms(Subject subject, List<String> programPublicIds) {
        if (programPublicIds == null) {
            return;
        }
        List<Program> programs = programPublicIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::requireProgram)
                .toList();
        // Un responsable pédagogique ne rattache une matière qu'à SES
        // formations ; le contrôle est fait formation par formation.
        programs.forEach(scopeGuard::requireProgramInScope);
        subjectProgramRepository.deleteBySubjectId(subject.getId());
        subjectProgramRepository.flush();
        Set<Long> distinct = new LinkedHashSet<>(programs.stream().map(Program::getId).toList());
        distinct.forEach(programId ->
                subjectProgramRepository.save(new SubjectProgram(subject.getId(), programId)));
    }

    private Subject require(UUID publicId) {
        return subjectRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.SUBJECT_NOT_FOUND));
    }

    private Program requireProgram(String publicId) {
        UUID id = AcademicWeb.parseUuid(publicId, AcademicException.Kind.PROGRAM_NOT_FOUND);
        return programRepository.findByPublicId(id)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    private SubjectResponse toResponse(Subject subject) {
        return toResponseWith(programsOf(List.of(subject))).apply(subject);
    }

    /**
     * Construit le convertisseur à partir d'un index préchargé : une seule
     * requête pour les rattachements de toute la page, jamais une par
     * ligne (NFR-PERF-08).
     */
    private Function<Subject, SubjectResponse> toResponseWith(
            Map<Long, List<SubjectResponse.ProgramRef>> index) {
        return subject -> new SubjectResponse(
                subject.getPublicId(),
                subject.getCode(),
                subject.getName(),
                subject.getDescription(),
                subject.getHourlyVolume(),
                subject.getStatus().name(),
                index.getOrDefault(subject.getId(), List.of()),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }

    private Map<Long, List<SubjectResponse.ProgramRef>> programsOf(List<Subject> subjects) {
        if (subjects.isEmpty()) {
            return Map.of();
        }
        List<Long> subjectIds = subjects.stream().map(Subject::getId).toList();
        List<SubjectProgram> links = subjectProgramRepository.findBySubjectIdIn(subjectIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, Program> programs = programRepository
                .findAllById(links.stream().map(SubjectProgram::getProgramId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Program::getId, Function.identity()));
        return links.stream()
                .filter(link -> programs.containsKey(link.getProgramId()))
                .collect(Collectors.groupingBy(SubjectProgram::getSubjectId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .map(link -> programs.get(link.getProgramId()))
                                .sorted(Comparator.comparing(Program::getCode))
                                .map(program -> new SubjectResponse.ProgramRef(
                                        program.getPublicId(), program.getCode(), program.getName()))
                                .toList())));
    }
}
