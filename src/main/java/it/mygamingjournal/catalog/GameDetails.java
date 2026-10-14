package it.mygamingjournal.catalog;

import java.time.LocalDate;
import java.util.List;

public record GameDetails(
        long id,
        String slug,
        String name,
        String description,
        LocalDate released,
        String coverImage,
        Double rating,
        Integer metacritic,
        String esrbRating,
        List<String> genres,
        List<String> platforms,
        Requirements requirements,
        List<String> developers,
        List<String> publishers,
        List<String> tags,
        List<String> screenshots,
        String redditUrl,
        Integer achievementsCount,
        CommunityStats community) {

    public GameDetails withCommunity(CommunityStats stats) {
        return new GameDetails(id, slug, name, description, released, coverImage, rating, metacritic, esrbRating,
                genres, platforms, requirements, developers, publishers, tags, screenshots, redditUrl,
                achievementsCount, stats);
    }

    public GameSummary toSummary() {
        return new GameSummary(id, slug, name, released, coverImage, rating, metacritic, platforms, genres);
    }
}
