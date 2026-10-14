package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RawgMapperTest {

    @Test
    void mapsDetailsIntoOurShape() {
        GameDetails details = RawgMapper.toDetails(RawgFixtures.gameDetails(3328, "Baldur's Gate 3"),
                List.of("https://media.rawg.io/shot.jpg"));

        assertThat(details.name()).isEqualTo("Baldur's Gate 3");
        assertThat(details.released()).isEqualTo(LocalDate.of(2023, 8, 3));
        assertThat(details.coverImage()).isEqualTo("https://media.rawg.io/cover-3328.jpg");
        assertThat(details.platforms()).containsExactly("pc");
        assertThat(details.genres()).containsExactly("RPG");
        assertThat(details.esrbRating()).isEqualTo("Mature");
        assertThat(details.requirements()).isEqualTo(new Requirements("min specs", "rec specs"));
        assertThat(details.screenshots()).containsExactly("https://media.rawg.io/shot.jpg");
        assertThat(details.community()).isEqualTo(CommunityStats.EMPTY);
    }

    @Test
    void stripsTheHeadingRawgPutsInFrontOfRequirements() {
        var raw = RawgFixtures.gameDetails(1, "X");
        var withHeadings = new RawgModels.RawgGameDetails(raw.id(), raw.slug(), raw.name(), raw.descriptionRaw(),
                raw.released(), raw.backgroundImage(), raw.rating(), raw.metacritic(), raw.esrbRating(),
                raw.redditUrl(), raw.achievementsCount(), raw.genres(), raw.parentPlatforms(),
                List.of(new RawgModels.RawgPlatformEntry(new RawgModels.RawgNamed(4, "PC", "pc"),
                        new RawgModels.RawgRequirements("Minimum:\nOS: Windows 10", "Recommended:\r\nOS: Windows 11"))),
                raw.developers(), raw.publishers(), raw.tags());

        GameDetails details = RawgMapper.toDetails(withHeadings, List.of());

        assertThat(details.requirements()).isEqualTo(new Requirements("OS: Windows 10", "OS: Windows 11"));
    }

    @Test
    void toleratesMissingOptionalFields() {
        RawgGame sparse = new RawgGame(1, "x", "X", "", null, null, null, null, null);

        GameSummary summary = RawgMapper.toSummary(sparse);

        assertThat(summary.released()).isNull();
        assertThat(summary.coverImage()).isNull();
        assertThat(summary.platforms()).isEmpty();
        assertThat(summary.genres()).isEmpty();
    }

    @Test
    void parsesAchievementPercentAndFallsBackToZero() {
        assertThat(RawgMapper.toAchievement(new RawgAchievement(1, "A", "d", "i", "37.25")).percent()).isEqualTo(37.25);
        assertThat(RawgMapper.toAchievement(new RawgAchievement(2, "B", "d", "i", "n/a")).percent()).isZero();
        assertThat(RawgMapper.toAchievement(new RawgAchievement(3, "C", "d", "i", null)).percent()).isZero();
    }
}
