package com.esic.connect.organization.internal;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination stable pour l'API (le format JSON par défaut de
 * {@link Page} est explicitement déconseillé côté client). Copie locale
 * au module : les DTO de {@code identity.internal} ne sont pas accessibles.
 */
record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
