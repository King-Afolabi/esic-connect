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
}
