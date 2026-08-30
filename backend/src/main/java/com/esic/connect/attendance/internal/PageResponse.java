package com.esic.connect.attendance.internal;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe de pagination stable exposée par l'API du module
 * {@code attendance} (mêmes champs que les autres modules).
 */
record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Pagination en mémoire d'une liste déjà construite (volume borné). */
    static <T> PageResponse<T> ofList(List<T> all, int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / safeSize);
        return new PageResponse<>(all.subList(from, to), safePage, safeSize, all.size(), totalPages);
    }
}
