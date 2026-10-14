package it.mygamingjournal.catalog;

import java.time.LocalDate;
import java.util.List;

public record GameSummary(long id, String slug, String name, LocalDate released, String coverImage,
                          Double rating, Integer metacritic, List<String> platforms, List<String> genres) {
}
