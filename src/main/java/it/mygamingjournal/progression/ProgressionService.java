package it.mygamingjournal.progression;

import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.library.LibraryStatsService;
import it.mygamingjournal.library.UserStats;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgressionService {

    private final LibraryStatsService stats;
    private final UserRepository users;
    private final LevelingSystem leveling;

    /** Call inside the transaction that changed entries or achievements. */
    @Transactional
    public void recalculate(long userId) {
        UserStats current = stats.statsFor(userId);
        int experience = leveling.calculateExp((int) current.totalGames(), (int) current.completedGames(),
                (int) current.unlockedAchievements(), current.hoursPlayed());
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setExperience(experience);
        user.setLevel(leveling.calculateLevel(experience));
    }
}
