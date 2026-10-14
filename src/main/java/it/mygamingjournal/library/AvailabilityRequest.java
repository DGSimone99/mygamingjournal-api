package it.mygamingjournal.library;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityRequest(
        @NotEmpty @Size(max = 10) List<@Pattern(regexp = "^[a-z0-9-]+$", message = "must be a platform slug") String> platforms,
        @NotEmpty @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") String> languages,
        @FutureOrPresent LocalDate availableUntil) {
}
