package it.mygamingjournal.user;

import it.mygamingjournal.library.UserStats;

import java.time.Instant;
import java.util.List;

public record MeResponse(long id, String username, String email, String displayName, String avatarUrl, String bio,
                         List<String> languages, GamingAccountsDto gamingAccounts, int experience, int level,
                         Instant createdAt, UserStats stats) {

    static MeResponse from(User user, UserStats stats) {
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBio(), user.getLanguages().stream().sorted().toList(),
                GamingAccountsDto.from(user.getGamingAccounts()), user.getExperience(), user.getLevel(),
                user.getCreatedAt(), stats);
    }
}
