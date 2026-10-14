package it.mygamingjournal.library;

import it.mygamingjournal.catalog.GameAchievement;

import java.time.Instant;

public record AchievementProgressResponse(long id, String name, String description, String image, double percent,
                                          boolean unlocked, Instant unlockedAt) {

    static AchievementProgressResponse of(GameAchievement achievement, Instant unlockedAt) {
        return new AchievementProgressResponse(achievement.id(), achievement.name(), achievement.description(),
                achievement.image(), achievement.percent(), unlockedAt != null, unlockedAt);
    }
}
