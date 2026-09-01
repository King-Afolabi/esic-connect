package com.esic.connect.planning.internal;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Enveloppe de pagination stable pour l'API (copie locale au module). */
record PlanningPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static <S, T> PlanningPageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PlanningPageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
