package com.lifelog.common.dto;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(boolean success, List<T> data, String error, PageMeta meta) {

    public static <T> PagedResponse<T> ok(Page<T> page) {
        return new PagedResponse<>(true, page.getContent(), null,
                new PageMeta(page.getNumber(), page.getSize(),
                             page.getTotalElements(), page.getTotalPages()));
    }

    public static <S, T> PagedResponse<T> ok(Page<S> page, Function<S, T> mapper) {
        return new PagedResponse<>(true, page.getContent().stream().map(mapper).toList(), null,
                new PageMeta(page.getNumber(), page.getSize(),
                             page.getTotalElements(), page.getTotalPages()));
    }

    public record PageMeta(int page, int size, long total, int totalPages) {}
}
