package it.mygamingjournal.review;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviews;

    @GetMapping("/games/{rawgGameId}/reviews")
    public PageResponse<ReviewResponse> listForGame(@PathVariable @Positive long rawgGameId, @Valid @ParameterObject PageParams paging) {
        return reviews.listForGame(rawgGameId, paging);
    }

    @GetMapping("/games/{rawgGameId}/reviews/me")
    public ReviewResponse getMine(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        return reviews.getMine(me.id(), rawgGameId);
    }

    @PutMapping("/games/{rawgGameId}/reviews/me")
    public ReviewResponse upsertMine(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @Valid @RequestBody ReviewRequest request) {
        return reviews.upsertMine(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/games/{rawgGameId}/reviews/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        reviews.deleteMine(me.id(), rawgGameId);
    }

    @GetMapping("/users/{userId}/reviews")
    public PageResponse<ReviewResponse> listForUser(@PathVariable @Positive long userId, @Valid @ParameterObject PageParams paging) {
        return reviews.listForUser(userId, paging);
    }
}
