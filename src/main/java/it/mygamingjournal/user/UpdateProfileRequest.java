package it.mygamingjournal.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Partial update: null fields are left unchanged. An empty bio clears it. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String displayName,
        @Size(max = 1000) String bio,
        @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") String> languages,
        @Valid GamingAccountsDto gamingAccounts) {
}
