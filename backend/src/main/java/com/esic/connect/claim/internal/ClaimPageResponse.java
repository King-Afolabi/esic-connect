package com.esic.connect.claim.internal;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Enveloppe de pagination, identique aux autres modules. */
record ClaimPageResponse(
        List<ClaimResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static ClaimPageResponse of(Page<Claim> page, Function<Claim, ClaimResponse> mapper) {
        return new ClaimPageResponse(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Variante « en bloc » (Lot 18, NFR-PERF-08) : {@code batchMapper}
     * résout tous les champs enrichis d'une page en un nombre borné de
     * requêtes, plutôt qu'un aller-retour par ligne.
     */
    static ClaimPageResponse ofBatch(Page<Claim> page, Function<List<Claim>, List<ClaimResponse>> batchMapper) {
        return new ClaimPageResponse(batchMapper.apply(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
