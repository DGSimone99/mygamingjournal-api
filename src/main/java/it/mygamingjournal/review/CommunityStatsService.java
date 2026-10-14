package it.mygamingjournal.review;

import it.mygamingjournal.catalog.CommunityStats;
import it.mygamingjournal.library.GameEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityStatsService {

    private final ReviewRepository reviews;
    private final GameEntryRepository entries;

    @Transactional(readOnly = true)
    public CommunityStats forGame(long rawgGameId) {
        ReviewRepository.StatsRow row = reviews.statsForGame(rawgGameId);
        Double average = row.getAverageScore() == null ? null : Math.round(row.getAverageScore() * 10) / 10.0;
        return new CommunityStats(average, row.getReviewCount(), entries.countByRawgGameId(rawgGameId));
    }
}
