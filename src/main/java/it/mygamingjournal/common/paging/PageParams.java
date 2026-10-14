package it.mygamingjournal.common.paging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageParams(@Min(1) Integer page, @Min(1) @Max(40) Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public PageParams {
        if (page == null) {
            page = 1;
        }
        if (pageSize == null) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, pageSize, sort);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
