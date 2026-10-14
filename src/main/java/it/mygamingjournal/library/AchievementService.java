package it.mygamingjournal.library;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.progression.ProgressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final LibraryService library;
    private final AchievementProgressRepository progress;
    private final CatalogService catalog;
    private final ProgressionService progression;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AchievementProgressResponse> list(long userId, long rawgGameId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        Map<Long, Instant> unlocked = progress.findByEntryId(entry.getId()).stream()
                .collect(Collectors.toMap(AchievementProgress::getRawgAchievementId, AchievementProgress::getUnlockedAt));
        return catalog.getAchievements(rawgGameId).stream()
                .map(achievement -> AchievementProgressResponse.of(achievement, unlocked.get(achievement.id())))
                .toList();
    }

    @Transactional
    public void unlock(long userId, long rawgGameId, long achievementId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        boolean belongsToGame = catalog.getAchievements(rawgGameId).stream()
                .anyMatch(achievement -> achievement.id() == achievementId);
        if (!belongsToGame) {
            throw new NotFoundException("Achievement " + achievementId + " not found for game " + rawgGameId);
        }
        AchievementProgressId id = new AchievementProgressId(entry.getId(), achievementId);
        if (!progress.existsById(id)) {
            progress.save(new AchievementProgress(entry.getId(), achievementId, clock.instant()));
            progression.recalculate(userId);
        }
    }

    @Transactional
    public void lock(long userId, long rawgGameId, long achievementId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        AchievementProgressId id = new AchievementProgressId(entry.getId(), achievementId);
        if (progress.existsById(id)) {
            progress.deleteById(id);
            progression.recalculate(userId);
        }
    }
}
