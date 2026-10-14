package it.mygamingjournal.library;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(List<String> platforms, List<String> languages, LocalDate availableUntil) {

    public static AvailabilityResponse from(PlayAvailability availability) {
        return new AvailabilityResponse(List.copyOf(availability.getPlatforms()),
                List.copyOf(availability.getLanguages()), availability.getAvailableUntil());
    }
}
