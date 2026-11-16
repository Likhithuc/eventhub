package com.eventhub.common.response;

import java.util.List;

import org.springframework.data.domain.Page;

/** Stable JSON shape for paginated lists (instead of serialising Spring's Page). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
