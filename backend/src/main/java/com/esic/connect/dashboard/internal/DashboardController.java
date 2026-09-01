package com.esic.connect.dashboard.internal;

import com.esic.connect.dashboard.internal.DashboardResponses.Dashboard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tableau de bord de l'appelant (bloc G1-F ; DEC-G1-010).
 * {@code GET /api/v1/me/dashboard} — authentifié ; le rôle effectif et le
 * périmètre sont décidés <strong>côté serveur</strong> à partir du JWT.
 *
 * <p>Paramètre facultatif {@code context} : le rôle sous lequel l'appelant
 * multi-rôles souhaite voir son tableau de bord (EF-AUTH-003). Il est
 * <strong>vérifié</strong> contre les autorités du JWT — un rôle non
 * détenu renvoie {@code 403}, jamais une élévation de privilèges. Absent,
 * le serveur retombe sur une priorité fixe déterministe.
 */
@RestController
class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/dashboard")
    @PreAuthorize("isAuthenticated()")
    Dashboard myDashboard(@AuthenticationPrincipal Jwt caller,
                          @RequestParam(name = "context", required = false) String context) {
        return service.forCaller(caller != null ? caller.getSubject() : null, roles(caller), context);
    }

    private static List<String> roles(Jwt caller) {
        if (caller == null) {
            return List.of();
        }
        List<String> claim = caller.getClaimAsStringList("roles");
        return claim != null ? claim : List.of();
    }
}
