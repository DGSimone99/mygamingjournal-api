package it.mygamingjournal.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotBlank @Size(max = 5000) String text, @NotNull @Min(1) @Max(10) Integer score) {
}
