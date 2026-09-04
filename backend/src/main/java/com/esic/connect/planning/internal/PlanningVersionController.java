package com.esic.connect.planning.internal;

import com.esic.connect.planning.internal.PlanningResponses.VersionDetailResponse;
import com.esic.connect.planning.internal.PlanningResponses.VersionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation des versions d'un planning publié (EF-PLAN-005/007).
 * Lecture réservée aux rôles de gestion ({@code PlanningWeb.MANAGE_ROLES}) ;
 * un {@code PEDAGOGICAL_MANAGER} est filtré par périmètre côté serveur.
 */
@RestController
@RequestMapping("/api/v1/planning/versions")
class PlanningVersionController {

    private final PlanningVersionService versionService;
    private final PlanningRollbackService rollbackService;
    private final com.esic.connect.identity.CurrentUserResolver currentUserResolver;

    PlanningVersionController(PlanningVersionService versionService,
                              PlanningRollbackService rollbackService,
                              com.esic.connect.identity.CurrentUserResolver currentUserResolver) {
        this.versionService = versionService;
        this.rollbackService = rollbackService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * Retour à une version antérieure (EF-PLAN-008 ; critère AC-009 ;
     * docs/02 §30.2 : {@code POST /planning/versions/{id}/rollback}).
     *
     * <p>Crée une version <strong>N+1</strong> dont le contenu est celui
     * de la version choisie. L'historique n'est jamais effacé (RG-046), et
     * l'identité des créneaux étant conservée, les séances existantes sont
     * réutilisées plutôt que recréées (RG-047).
     */
    @org.springframework.web.bind.annotation.PostMapping("/{publicId}/rollback")
    @org.springframework.security.access.prepost.PreAuthorize(PlanningWeb.MANAGE_ROLES)
    PlanningRollbackService.RollbackResult rollback(
            @org.springframework.web.bind.annotation.PathVariable String publicId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt caller) {
        Long actorId = currentUserResolver.resolveInternalId(PlanningWeb.subject(caller))
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN));
        return rollbackService.rollback(
                PlanningWeb.parseUuid(publicId, PlanningException.Kind.VERSION_NOT_FOUND), actorId);
    }

    @GetMapping
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    PlanningPageResponse<VersionResponse> list(@RequestParam("classGroupPublicId") String classGroupPublicId,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return versionService.listForClass(classGroupPublicId, page, size, sort);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(PlanningWeb.MANAGE_ROLES)
    VersionDetailResponse get(@PathVariable String publicId) {
        return versionService.get(publicId);
    }
}
