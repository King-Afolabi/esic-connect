package com.esic.connect.attendance.internal;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Départ anticipé (EF-ATT-013 ; docs/02 §16.13 et §30.2).
 *
 * <p>La création est réservée à l'apprenant : c'est lui qui signale son
 * départ. L'inscription visée n'est jamais un paramètre — le serveur la
 * déduit du JWT et de la séance, faute de quoi n'importe qui pourrait
 * déposer un dossier au nom d'un autre.
 *
 * <p>Le traitement (transmission, décision) est réservé aux rôles qui
 * tiennent la séance ; le service applique ensuite le contrôle fin :
 * séance du formateur, périmètre pédagogique, et réserve au responsable
 * la décision d'un dossier transmis.
 */
@RestController
class EarlyDepartureController {

    private final EarlyDepartureService service;

    EarlyDepartureController(EarlyDepartureService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/attendance/early-departure")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    @ResponseStatus(HttpStatus.CREATED)
    EarlyDepartureResponse declare(@Valid @RequestBody EarlyDepartureRequests.Declare request,
                                   @AuthenticationPrincipal Jwt caller) {
        return service.declare(AttendanceManagementWeb.subject(caller), request);
    }

    @GetMapping("/api/v1/me/attendance/early-departures")
    @PreAuthorize(AttendanceManagementWeb.STUDENT_ROLE)
    List<EarlyDepartureResponse> listOwn(@AuthenticationPrincipal Jwt caller) {
        return service.listOwn(AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/api/v1/sessions/{sessionId}/attendance/early-departures")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    List<EarlyDepartureResponse> listForSession(@PathVariable String sessionId) {
        return service.listForSession(sessionId);
    }

    @PostMapping("/api/v1/attendance/early-departures/{departureId}/forward")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    EarlyDepartureResponse forward(@PathVariable String departureId,
                                   @Valid @RequestBody EarlyDepartureRequests.Forward request,
                                   @AuthenticationPrincipal Jwt caller) {
        return service.forward(departureId, request, AttendanceManagementWeb.subject(caller));
    }

    @PostMapping("/api/v1/attendance/early-departures/{departureId}/decision")
    @PreAuthorize(AttendanceManagementWeb.MANAGE_ROLES)
    EarlyDepartureResponse decide(@PathVariable String departureId,
                                  @Valid @RequestBody EarlyDepartureRequests.Decide request,
                                  @AuthenticationPrincipal Jwt caller) {
        return service.decide(departureId, request, AttendanceManagementWeb.subject(caller));
    }
}
