package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.planning.internal.PlanningResponses.JobResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Construction directe du planning dans un calendrier (EF-PLAN-006 ;
 * docs/02 §13.7 et §30.2 : {@code GET /planning/calendar},
 * {@code POST /planning/slots}).
 *
 * <p>Le brouillon produit est un travail d'import ordinaire : il se publie
 * par {@code POST /planning-imports/{id}/publish}, avec les mêmes
 * contrôles de conflit, le même verrou et le même versionnement. Rien
 * n'est raccourci parce que la saisie est manuelle.
 */
@RestController
@RequestMapping("/api/v1/planning")
class PlanningCalendarController {

    private final PlanningCalendarService calendarService;
    private final PlanningQueryService queryService;
    private final CurrentUserResolver currentUserResolver;
    private final AcademicScopeDirectory academicScopeDirectory;

    PlanningCalendarController(PlanningCalendarService calendarService,
                               PlanningQueryService queryService,
                               CurrentUserResolver currentUserResolver,
                               AcademicScopeDirectory academicScopeDirectory) {
        this.calendarService = calendarService;
        this.queryService = queryService;
        this.currentUserResolver = currentUserResolver;
        this.academicScopeDirectory = academicScopeDirectory;
    }

    /**
     * Créneau saisi au calendrier. Les valeurs restent des chaînes :
     * elles traversent le même analyseur que les colonnes d'un fichier,
     * donc une heure mal formée produit la même anomalie, lisible au même
     * endroit, plutôt qu'un {@code 400} muet du désérialiseur.
     */
    record SlotRequest(
            @Size(max = 64) String slotKey,
            @NotBlank @Size(max = 40) String sessionDate,
            @NotBlank @Size(max = 20) String startTime,
            @NotBlank @Size(max = 20) String endTime,
            @NotBlank @Size(max = 64) String timeZoneId,
            @NotBlank @Size(max = 191) String title,
            @Size(max = 64) String teacherPublicId,
            @Size(max = 50) String roomCode) {

        PlanningCalendarService.SlotValues toValues() {
            return new PlanningCalendarService.SlotValues(slotKey, sessionDate, startTime, endTime,
                    timeZoneId, title, teacherPublicId, roomCode);
        }
    }

    /** Duplication d'une semaine vers une autre. */
    record DuplicateWeekRequest(
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sourceWeekStart,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetWeekStart) {
    }

    /** Répétition d'un créneau : {@code occurrences} copies supplémentaires. */
    record RepeatRequest(
            @NotNull Integer occurrences,
            @NotNull Integer everyDays) {
    }

    @GetMapping("/calendar")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    PlanningCalendarService.CalendarView calendar(
            @RequestParam String classGroupPublicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt caller) {
        return calendarService.calendar(
                PlanningWeb.parseUuid(classGroupPublicId, PlanningException.Kind.TARGET_UNRESOLVED),
                from, to, actor(caller));
    }

    @PostMapping("/slots")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    JobResponse addSlot(@RequestParam String classGroupPublicId,
                        @Valid @RequestBody SlotRequest request,
                        @AuthenticationPrincipal Jwt caller) {
        Long actor = actor(caller);
        PlanningImportJob job = calendarService.addSlot(
                PlanningWeb.parseUuid(classGroupPublicId, PlanningException.Kind.TARGET_UNRESOLVED),
                request.toValues(), actor);
        return job(job, actor);
    }

    @PatchMapping("/drafts/{jobId}/slots/{rowId}")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    JobResponse updateSlot(@PathVariable String jobId, @PathVariable String rowId,
                           @Valid @RequestBody SlotRequest request,
                           @AuthenticationPrincipal Jwt caller) {
        Long actor = actor(caller);
        PlanningImportJob job = calendarService.updateSlot(
                PlanningWeb.parseUuid(jobId, PlanningException.Kind.JOB_NOT_FOUND),
                PlanningWeb.parseUuid(rowId, PlanningException.Kind.ROW_NOT_FOUND),
                request.toValues(), actor, academicScopeDirectory.hasGlobalScope());
        return job(job, actor);
    }

    @DeleteMapping("/drafts/{jobId}/slots/{rowId}")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    JobResponse removeSlot(@PathVariable String jobId, @PathVariable String rowId,
                           @AuthenticationPrincipal Jwt caller) {
        Long actor = actor(caller);
        PlanningImportJob job = calendarService.removeSlot(
                PlanningWeb.parseUuid(jobId, PlanningException.Kind.JOB_NOT_FOUND),
                PlanningWeb.parseUuid(rowId, PlanningException.Kind.ROW_NOT_FOUND),
                actor, academicScopeDirectory.hasGlobalScope());
        return job(job, actor);
    }

    @PostMapping("/drafts/{jobId}/duplicate-week")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    JobResponse duplicateWeek(@PathVariable String jobId,
                              @Valid @RequestBody DuplicateWeekRequest request,
                              @AuthenticationPrincipal Jwt caller) {
        Long actor = actor(caller);
        PlanningImportJob job = calendarService.duplicateWeek(
                PlanningWeb.parseUuid(jobId, PlanningException.Kind.JOB_NOT_FOUND),
                request.sourceWeekStart(), request.targetWeekStart(), actor,
                academicScopeDirectory.hasGlobalScope());
        return job(job, actor);
    }

    @PostMapping("/drafts/{jobId}/slots/{rowId}/repeat")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    JobResponse repeatSlot(@PathVariable String jobId, @PathVariable String rowId,
                           @Valid @RequestBody RepeatRequest request,
                           @AuthenticationPrincipal Jwt caller) {
        Long actor = actor(caller);
        PlanningImportJob job = calendarService.repeatSlot(
                PlanningWeb.parseUuid(jobId, PlanningException.Kind.JOB_NOT_FOUND),
                PlanningWeb.parseUuid(rowId, PlanningException.Kind.ROW_NOT_FOUND),
                request.occurrences(), request.everyDays(), actor,
                academicScopeDirectory.hasGlobalScope());
        return job(job, actor);
    }

    private JobResponse job(PlanningImportJob job, Long actor) {
        return queryService.get(job.getPublicId().toString(), actor,
                academicScopeDirectory.hasGlobalScope());
    }

    private Long actor(Jwt caller) {
        return currentUserResolver.resolveInternalId(PlanningWeb.subject(caller))
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN));
    }
}
