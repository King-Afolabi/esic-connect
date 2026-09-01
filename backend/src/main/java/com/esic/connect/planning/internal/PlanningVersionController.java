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

    PlanningVersionController(PlanningVersionService versionService) {
        this.versionService = versionService;
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
