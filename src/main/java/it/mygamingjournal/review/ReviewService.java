package it.mygamingjournal.review;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.ProfileService;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/** As in LibraryService, RAWG is only called outside database transactions. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ProfileService profiles;
    private final CatalogService catalog;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForGame(long rawgGameId, PageParams paging) {
        Page<Review> page = reviews.findByRawgGameId(rawgGameId, paging.toPageable(NEWEST_FIRST));
        Map<Long, UserSummary> authors = authorsOf(page);
        return PageResponse.of(page, paging, review -> ReviewResponse.of(review, authors.get(review.getUserId()), null));
    }

    public PageResponse<ReviewResponse> listForUser(long userId, PageParams paging) {
        UserReviews loaded = transaction.execute(tx -> new UserReviews(
                UserSummary.from(profiles.requireUser(userId)),
                reviews.findByUserId(userId, paging.toPageable(NEWEST_FIRST))));
        Map<Long, GameSummary> games = catalog.getSummaries(
                loaded.page().getContent().stream().map(Review::getRawgGameId).toList());
        return PageResponse.of(loaded.page(), paging,
                review -> ReviewResponse.of(review, loaded.author(), games.get(review.getRawgGameId())));
    }

    public ReviewResponse upsertMine(long userId, long rawgGameId, ReviewRequest request) {
        if (reviews.findByUserIdAndRawgGameId(userId, rawgGameId).isEmpty()) {
            catalog.getGame(rawgGameId); // 404 if RAWG does not know this game
        }
        return transaction.execute(tx -> {
            Instant now = clock.instant();
            Review review = reviews.findByUserIdAndRawgGameId(userId, rawgGameId)
                    .orElseGet(() -> new Review(userId, rawgGameId, now));
            review.setText(request.text().trim());
            review.setScore(request.score());
            review.setUpdatedAt(now);
            reviews.save(review);
            return ReviewResponse.of(review, UserSummary.from(profiles.requireUser(userId)), null);
        });
    }

    @Transactional
    public void deleteMine(long userId, long rawgGameId) {
        reviews.deleteByUserIdAndRawgGameId(userId, rawgGameId);
    }

    private Map<Long, UserSummary> authorsOf(Page<Review> page) {
        return users.findAllById(page.getContent().stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
    }

    private record UserReviews(UserSummary author, Page<Review> page) {
    }
}
