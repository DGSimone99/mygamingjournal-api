package it.mygamingjournal.library;

public record UserStats(long totalGames, long completedGames, long wishlistGames, double hoursPlayed,
                        long unlockedAchievements) {
}
