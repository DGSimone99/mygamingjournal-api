package it.mygamingjournal.catalog;

import it.mygamingjournal.common.paging.PageParams;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/** Catalog filters. The v1 views (top, new, coming, genre, developer) are combinations of these. */
public record GameQuery(
        @Size(max = 100) String search,
        @Pattern(regexp = "^[a-z0-9-]+$") String genre,
        @Pattern(regexp = "^[a-z0-9-]+$") String developer,
        @Pattern(regexp = "^\\d+$") String platform,
        @Pattern(regexp = "^-?(name|released|added|created|updated|rating|metacritic)$") String ordering,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2},\\d{4}-\\d{2}-\\d{2}$") String dates) {

    Map<String, String> toRawgParams(PageParams paging) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "search", search);
        putIfPresent(params, "genres", genre);
        putIfPresent(params, "developers", developer);
        putIfPresent(params, "parent_platforms", platform);
        putIfPresent(params, "ordering", ordering);
        putIfPresent(params, "dates", dates);
        params.put("page", Integer.toString(paging.page()));
        params.put("page_size", Integer.toString(paging.pageSize()));
        return params;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }
}
