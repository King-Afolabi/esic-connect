package com.esic.connect.search.internal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche globale (EF-USER-009 ; docs/02 §22.7).
 *
 * <p>Réservée aux rôles disposant d'un périmètre — global pour
 * l'administration, pédagogique pour un responsable. Un
 * {@code TEACHER} et un {@code STUDENT} en sont exclus : leur besoin est
 * couvert par leurs propres écrans, et leur ouvrir un moteur sur le
 * référentiel reviendrait à leur ouvrir l'annuaire.
 */
@RestController
@RequestMapping("/api/v1/search")
class GlobalSearchController {

    private final GlobalSearchService service;

    GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')")
    SearchResponses.GlobalSearch search(@RequestParam(name = "q", required = false) String query) {
        return service.search(query);
    }
}
