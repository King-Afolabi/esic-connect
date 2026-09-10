package com.esic.connect.myplanning.internal;

import com.esic.connect.myplanning.internal.MyPlanningResponses.MyPlanning;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * « Mon planning » (CDC §5.6/§5.7). {@code GET /api/v1/me/planning} —
 * authentifié, réservé à {@code TEACHER} et {@code STUDENT} : les autres
 * rôles ont déjà une consultation dédiée ({@code GET /api/v1/sessions}
 * pour l'administration/la gestion pédagogique, {@code /planning/calendar}
 * pour la construction). Le périmètre est décidé côté serveur à partir du
 * rôle effectif du JWT, jamais d'un paramètre client.
 */
@RestController
class MyPlanningController {

    private final MyPlanningService service;

    MyPlanningController(MyPlanningService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/planning")
    @PreAuthorize("hasAnyRole('TEACHER','STUDENT')")
    MyPlanning myPlanning(@AuthenticationPrincipal Jwt caller,
                         @RequestParam(required = false) Instant from,
                         @RequestParam(required = false) Instant to) {
        return service.forCaller(caller != null ? caller.getSubject() : null, roles(caller), from, to);
    }

    private static List<String> roles(Jwt caller) {
        if (caller == null) {
            return List.of();
        }
        List<String> claim = caller.getClaimAsStringList("roles");
        return claim != null ? claim : List.of();
    }
}
