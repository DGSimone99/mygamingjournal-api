package it.mygamingjournal.auth;

import it.mygamingjournal.user.UserSummary;

public record AuthResponse(String accessToken, UserSummary user) {
}
