package com.esic.connect.coursesession.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Points de contrôle d'émargement d'une séance (V10). Identifiants
 * exclusivement en {@code public_id}. Le contrôle fin de périmètre est
 * appliqué par {@link AttendanceCheckpointService} /
 * {@link CourseSessionAccessGuard} (lecture = {@code READ_ROLES},
 * gestion = {@code MANAGE_ROLES}, {@code SCHOOL_ADMINISTRATION} exclu de
 * la gestion). {@code STUDENT} n'a aucun accès.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/checkpoints")
class AttendanceCheckpointController {

    private final AttendanceCheckpointService service;

    AttendanceCheckpointController(AttendanceCheckpointService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(CourseSessionWeb.READ_ROLES)
    List<CheckpointResponse> list(@PathVariable String sessionId, @AuthenticationPrincipal Jwt caller) {
        return service.list(sessionId, CourseSessionWeb.subject(caller));
    }

    @PostMapping
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    CheckpointResponse create(@PathVariable String sessionId,
                              @Valid @RequestBody CheckpointRequests.Create request,
                              @AuthenticationPrincipal Jwt caller) {
        return service.create(sessionId, request, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{checkpointId}/open")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void open(@PathVariable String sessionId, @PathVariable String checkpointId,
              @AuthenticationPrincipal Jwt caller) {
        service.open(sessionId, checkpointId, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{checkpointId}/close")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String sessionId, @PathVariable String checkpointId,
               @AuthenticationPrincipal Jwt caller) {
        service.close(sessionId, checkpointId, CourseSessionWeb.subject(caller));
    }

    @PostMapping("/{checkpointId}/cancel")
    @PreAuthorize(CourseSessionWeb.MANAGE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String sessionId, @PathVariable String checkpointId,
                @Valid @RequestBody CheckpointRequests.Cancel request,
                @AuthenticationPrincipal Jwt caller) {
        service.cancel(sessionId, checkpointId, request, CourseSessionWeb.subject(caller));
    }
}
