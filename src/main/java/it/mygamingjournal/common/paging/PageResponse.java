package it.mygamingjournal.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int pageSize, long totalItems) {

    public static <E, T> PageResponse<T> of(Page<E> page, PageParams params, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                params.page(), params.pageSize(), page.getTotalElements());
    }
}
