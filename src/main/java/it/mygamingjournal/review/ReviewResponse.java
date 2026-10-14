package it.mygamingjournal.review;

import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.user.UserSummary;

import java.time.Instant;

/** game is filled only when listing a user's reviews; author is null if the user no longer exists. */
public record ReviewResponse(long id, long rawgGameId, String text, int score, Instant createdAt, Instant updatedAt,
                             UserSummary author, GameSummary game) {

    static ReviewResponse of(Review review, UserSummary author, GameSummary game) {
        return new ReviewResponse(review.getId(), review.getRawgGameId(), review.getText(), review.getScore(),
                review.getCreatedAt(), review.getUpdatedAt(), author, game);
    }
}
