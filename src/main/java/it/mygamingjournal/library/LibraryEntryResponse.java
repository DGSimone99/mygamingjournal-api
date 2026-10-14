package it.mygamingjournal.library;

import it.mygamingjournal.catalog.GameSummary;

import java.time.Instant;

public record LibraryEntryResponse(
        long rawgGameId,
        GameStatus status,
        Double personalRating,
        double hoursPlayed,
        String notes,
        Instant addedAt,
        Instant updatedAt,
        long unlockedAchievements,
        AvailabilityResponse availability,
        GameSummary game) {

    static LibraryEntryResponse of(GameEntry entry, long unlockedAchievements, PlayAvailability availability,
                                   GameSummary game) {
        return new LibraryEntryResponse(entry.getRawgGameId(), entry.getStatus(), entry.getPersonalRating(),
                entry.getHoursPlayed(), entry.getNotes(), entry.getAddedAt(), entry.getUpdatedAt(),
                unlockedAchievements, availability == null ? null : AvailabilityResponse.from(availability), game);
    }
}
