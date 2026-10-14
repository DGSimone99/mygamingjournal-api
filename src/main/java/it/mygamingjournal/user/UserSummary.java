package it.mygamingjournal.user;

public record UserSummary(long id, String username, String displayName, String avatarUrl, int level) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarUrl(), user.getLevel());
    }
}
