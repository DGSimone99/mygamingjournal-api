package it.mygamingjournal.user;

import it.mygamingjournal.library.UserStats;

import java.time.Instant;
import java.util.List;

/** No email: this is visible to everyone. followedByMe is null for anonymous viewers. */
public record PublicProfileResponse(long id, String username, String displayName, String avatarUrl, String bio,
                                    List<String> languages, GamingAccountsDto gamingAccounts, int experience,
                                    int level, Instant createdAt, UserStats stats, Boolean followedByMe) {

    static PublicProfileResponse from(User user, UserStats stats, Boolean followedByMe) {
        return new PublicProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBio(), user.getLanguages().stream().sorted().toList(),
                GamingAccountsDto.from(user.getGamingAccounts()), user.getExperience(), user.getLevel(),
                user.getCreatedAt(), stats, followedByMe);
    }
}
