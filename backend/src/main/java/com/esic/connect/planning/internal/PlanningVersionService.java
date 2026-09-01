package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.planning.internal.PlanningResponses.VersionDetailResponse;
import com.esic.connect.planning.internal.PlanningResponses.VersionEntryResponse;
import com.esic.connect.planning.internal.PlanningResponses.VersionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consultation des versions d'un planning (EF-PLAN-005/007 ; RG-032 :
 * versions jamais supprimées). Périmètre : un {@code PEDAGOGICAL_MANAGER}
 * ne voit que les plannings de ses classes ({@code AcademicScopeDirectory}).
 */
@Service
class PlanningVersionService {

    private static final Set<String> SORTABLE = Set.of("versionNumber", "publishedAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "versionNumber");

    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;

    PlanningVersionService(PlanningScheduleRepository scheduleRepository,
                           PlanningVersionRepository versionRepository,
                           PlanningEntryRepository entryRepository,
                           ClassGroupDirectory classGroupDirectory,
                           AcademicScopeDirectory academicScopeDirectory) {
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
    }

    @Transactional(readOnly = true)
    PlanningPageResponse<VersionResponse> listForClass(String classGroupPublicId, int page, int size, String sort) {
        UUID classId = parseUuid(classGroupPublicId);
        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory.findByPublicId(classId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCHEDULE_NOT_FOUND));
        if (!academicScopeDirectory.hasGlobalScope() && !academicScopeDirectory.isClassInScope(classId)) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        PlanningSchedule schedule = scheduleRepository
                .findByClassGroupIdAndAcademicYearId(classRef.internalId(), classRef.academicYearInternalId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCHEDULE_NOT_FOUND));
        Pageable pageable = PlanningQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        Page<PlanningVersion> versions = versionRepository.findBySchedule_Id(schedule.getId(), pageable);
        return PlanningPageResponse.of(versions, v -> toVersionResponse(v, schedule, classRef));
    }

    @Transactional(readOnly = true)
    VersionDetailResponse get(String versionPublicId) {
        UUID id = parseUuid(versionPublicId);
        PlanningVersion version = versionRepository.findByPublicId(id)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.VERSION_NOT_FOUND));
        PlanningSchedule schedule = version.getSchedule();
        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory.findByInternalId(schedule.getClassGroupId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCHEDULE_NOT_FOUND));
        if (!academicScopeDirectory.hasGlobalScope() && !academicScopeDirectory.isClassInScope(classRef.publicId())) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        List<VersionEntryResponse> entries = entryRepository
                .findByPlanningVersion_IdOrderByStartsAtAsc(version.getId()).stream()
                .map(e -> new VersionEntryResponse(e.getPublicId(), e.getSlotPublicId(), e.getSlotKey(),
                        e.getTitle(), e.getStartsAt(), e.getEndsAt(), e.getTimeZoneId(), e.getRoomCode(),
                        e.getSessionPublicId()))
                .toList();
        return new VersionDetailResponse(toVersionResponse(version, schedule, classRef), entries);
    }

    private static VersionResponse toVersionResponse(PlanningVersion version, PlanningSchedule schedule,
                                                     ClassGroupDirectory.ClassGroupRef classRef) {
        UUID replacedBy = version.getReplacedByVersion() != null
                ? version.getReplacedByVersion().getPublicId() : null;
        return new VersionResponse(version.getPublicId(), schedule.getPublicId(), classRef.publicId(),
                classRef.academicYearPublicId(), version.getVersionNumber(), version.getStatus().name(),
                version.getEntryCount(), version.getChangeSummary(), replacedBy,
                version.getPublishedAt(), version.getCreatedAt());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new PlanningException(PlanningException.Kind.VERSION_NOT_FOUND);
        }
    }
}
