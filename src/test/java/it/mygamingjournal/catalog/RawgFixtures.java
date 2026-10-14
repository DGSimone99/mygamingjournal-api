package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgParentPlatform;
import it.mygamingjournal.catalog.RawgModels.RawgPlatformEntry;
import it.mygamingjournal.catalog.RawgModels.RawgRequirements;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public final class RawgFixtures {

    private RawgFixtures() {
    }

    public static RawgGameDetails gameDetails(long id, String name) {
        return new RawgGameDetails(id, "game-" + id, name, "Description of " + name, "2023-08-03",
                "https://media.rawg.io/cover-" + id + ".jpg", 4.5, 96,
                new RawgNamed(4, "Mature", "mature"), "https://reddit.com/r/game" + id, 2,
                List.of(new RawgNamed(5, "RPG", "role-playing-games-rpg")),
                List.of(new RawgParentPlatform(new RawgNamed(1, "PC", "pc"))),
                List.of(new RawgPlatformEntry(new RawgNamed(4, "PC", "pc"), new RawgRequirements("min specs", "rec specs"))),
                List.of(new RawgNamed(10, "Larian Studios", "larian-studios")),
                List.of(new RawgNamed(10, "Larian Studios", "larian-studios")),
                List.of(new RawgNamed(31, "Singleplayer", "singleplayer")));
    }

    public static RawgGame game(long id, String name) {
        return new RawgGame(id, "game-" + id, name, "2023-08-03", "https://media.rawg.io/cover-" + id + ".jpg", 4.5, 96,
                List.of(new RawgParentPlatform(new RawgNamed(1, "PC", "pc"))),
                List.of(new RawgNamed(5, "RPG", "role-playing-games-rpg")));
    }

    public static <T> RawgPage<T> page(List<T> items, String next) {
        return new RawgPage<>(items.size(), next, items);
    }

    public static RawgPage<RawgScreenshot> screenshots(long gameId) {
        return page(List.of(new RawgScreenshot(1, "https://media.rawg.io/shot-" + gameId + ".jpg")), null);
    }

    public static RawgAchievement achievement(long id, String name) {
        return new RawgAchievement(id, name, name + " description", "https://media.rawg.io/ach-" + id + ".jpg", "12.50");
    }

    public static void stubGame(RawgApi rawg, long id, String name) {
        when(rawg.getGame(id)).thenReturn(gameDetails(id, name));
        when(rawg.getScreenshots(id)).thenReturn(screenshots(id));
    }

    public static void stubAchievements(RawgApi rawg, long gameId, RawgAchievement... achievements) {
        when(rawg.getAchievements(eq(gameId), eq(1), anyInt())).thenReturn(page(List.of(achievements), null));
    }
}
