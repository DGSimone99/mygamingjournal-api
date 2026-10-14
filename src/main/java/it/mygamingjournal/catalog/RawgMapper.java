package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgParentPlatform;
import it.mygamingjournal.catalog.RawgModels.RawgPlatformEntry;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

final class RawgMapper {

    private RawgMapper() {
    }

    static GameSummary toSummary(RawgGame game) {
        return new GameSummary(game.id(), game.slug(), game.name(), parseDate(game.released()),
                blankToNull(game.backgroundImage()), game.rating(), game.metacritic(),
                platformSlugs(game.parentPlatforms()), names(game.genres()));
    }

    static GameDetails toDetails(RawgGameDetails game, List<String> screenshots) {
        return new GameDetails(game.id(), game.slug(), game.name(), blankToNull(game.descriptionRaw()),
                parseDate(game.released()), blankToNull(game.backgroundImage()), game.rating(), game.metacritic(),
                game.esrbRating() == null ? null : game.esrbRating().name(),
                names(game.genres()), platformSlugs(game.parentPlatforms()), pcRequirements(game.platforms()),
                names(game.developers()), names(game.publishers()), names(game.tags()),
                List.copyOf(screenshots), blankToNull(game.redditUrl()), game.achievementsCount(),
                CommunityStats.EMPTY);
    }

    static GameAchievement toAchievement(RawgAchievement achievement) {
        return new GameAchievement(achievement.id(), achievement.name(), achievement.description(),
                blankToNull(achievement.image()), parsePercent(achievement.percent()));
    }

    static NamedItem toNamed(RawgNamed named) {
        return new NamedItem(named.id(), named.name(), named.slug());
    }

    private static Requirements pcRequirements(List<RawgPlatformEntry> platforms) {
        if (platforms == null) {
            return null;
        }
        return platforms.stream()
                .filter(entry -> entry.platform() != null && "pc".equals(entry.platform().slug()))
                .map(RawgPlatformEntry::requirements)
                .filter(Objects::nonNull)
                .filter(req -> req.minimum() != null || req.recommended() != null)
                .map(req -> new Requirements(req.minimum(), req.recommended()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> platformSlugs(List<RawgParentPlatform> platforms) {
        if (platforms == null) {
            return List.of();
        }
        return platforms.stream()
                .map(RawgParentPlatform::platform)
                .filter(Objects::nonNull)
                .map(RawgNamed::slug)
                .toList();
    }

    private static List<String> names(List<RawgNamed> items) {
        return items == null ? List.of() : items.stream().map(RawgNamed::name).toList();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static double parsePercent(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
