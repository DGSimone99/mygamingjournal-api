package it.mygamingjournal.matchmaking;

import it.mygamingjournal.user.UserSummary;

import java.time.LocalDate;
import java.util.List;

public record AvailablePlayerResponse(UserSummary user, List<String> platforms, List<String> languages,
                                      LocalDate availableUntil) {
}
