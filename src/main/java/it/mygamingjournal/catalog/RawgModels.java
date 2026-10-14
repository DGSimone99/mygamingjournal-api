package it.mygamingjournal.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** RAWG JSON shapes. Internal to the catalog package: never returned by our API. */
public final class RawgModels {

    private RawgModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgPage<T>(int count, String next, List<T> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgNamed(long id, String name, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgParentPlatform(RawgNamed platform) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgRequirements(String minimum, String recommended) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgPlatformEntry(RawgNamed platform, RawgRequirements requirements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgGame(
            long id,
            String slug,
            String name,
            String released,
            @JsonProperty("background_image") String backgroundImage,
            Double rating,
            Integer metacritic,
            @JsonProperty("parent_platforms") List<RawgParentPlatform> parentPlatforms,
            List<RawgNamed> genres) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgGameDetails(
            long id,
            String slug,
            String name,
            @JsonProperty("description_raw") String descriptionRaw,
            String released,
            @JsonProperty("background_image") String backgroundImage,
            Double rating,
            Integer metacritic,
            @JsonProperty("esrb_rating") RawgNamed esrbRating,
            @JsonProperty("reddit_url") String redditUrl,
            @JsonProperty("achievements_count") Integer achievementsCount,
            List<RawgNamed> genres,
            @JsonProperty("parent_platforms") List<RawgParentPlatform> parentPlatforms,
            List<RawgPlatformEntry> platforms,
            List<RawgNamed> developers,
            List<RawgNamed> publishers,
            List<RawgNamed> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgScreenshot(long id, String image) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgAchievement(long id, String name, String description, String image, String percent) {
    }
}
