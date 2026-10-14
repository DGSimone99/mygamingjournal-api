package it.mygamingjournal.library;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Full replacement (PUT): omitted optional fields are cleared (rating null, hours 0, notes empty). */
public record UpsertEntryRequest(
        @NotNull GameStatus status,
        @DecimalMin("0.0") @DecimalMax("10.0") Double personalRating,
        @PositiveOrZero @Max(100_000) Double hoursPlayed,
        @Size(max = 5000) String notes) {
}
