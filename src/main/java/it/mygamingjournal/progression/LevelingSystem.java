package it.mygamingjournal.progression;

import org.springframework.stereotype.Component;

@Component
public class LevelingSystem {

    private static final int EXP_PER_LEVEL = 1000;
    private static final int WEIGHT_TOTAL_GAMES = 40;
    private static final int WEIGHT_COMPLETED_GAMES = 120;
    private static final int WEIGHT_UNLOCKED_ACHIEVEMENTS = 15;
    private static final int WEIGHT_HOURS_PLAYED = 5;

    public int calculateExp(int totalGames, int completedGames, int unlockedAchievements, double hoursPlayed) {
        int hours = (int) Math.floor(hoursPlayed);
        return totalGames * WEIGHT_TOTAL_GAMES
                + completedGames * WEIGHT_COMPLETED_GAMES
                + unlockedAchievements * WEIGHT_UNLOCKED_ACHIEVEMENTS
                + hours * WEIGHT_HOURS_PLAYED;
    }

    public int calculateLevel(int totalExp) {
        return totalExp / EXP_PER_LEVEL;
    }
}
