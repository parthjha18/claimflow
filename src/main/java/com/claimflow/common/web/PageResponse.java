package com.claimflow.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable pagination envelope (Spring's PageImpl JSON is not a supported contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
