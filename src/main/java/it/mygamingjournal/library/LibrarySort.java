package it.mygamingjournal.library;

import it.mygamingjournal.common.error.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

public enum LibrarySort {
    ADDED_AT("addedAt", "addedAt"),
    UPDATED_AT("updatedAt", "updatedAt"),
    RATING("rating", "personalRating"),
    HOURS("hours", "hoursPlayed");

    private final String param;
    private final String property;

    LibrarySort(String param, String property) {
        this.param = param;
        this.property = property;
    }

    public static LibrarySort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return ADDED_AT;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.param.equals(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unsupported sort '" + value + "'. Use addedAt, updatedAt, rating or hours"));
    }

    Sort toSort() {
        return Sort.by(Sort.Order.desc(property).nullsLast(), Sort.Order.desc("id"));
    }
}
