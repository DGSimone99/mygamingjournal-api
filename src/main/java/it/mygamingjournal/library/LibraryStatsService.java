package it.mygamingjournal.library;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryStatsService {

    private final GameEntryRepository entries;
    private final AchievementProgressRepository progress;

    @Transactional(readOnly = true)
    public UserStats statsFor(long userId) {
        GameEntryRepository.StatsRow row = entries.statsForUser(userId);
        return new UserStats(row.getTotalGames(), row.getCompletedGames(), row.getWishlistGames(),
                row.getHoursPlayed(), progress.countUnlockedByUserId(userId));
    }
}
