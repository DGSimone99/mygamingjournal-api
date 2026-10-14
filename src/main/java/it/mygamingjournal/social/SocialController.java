package it.mygamingjournal.social;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.UserSummary;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/following")
@RequiredArgsConstructor
public class SocialController {

    private final FollowService follows;

    @GetMapping
    public PageResponse<UserSummary> following(@AuthenticationPrincipal AuthUser me, @Valid @ParameterObject PageParams paging) {
        return follows.following(me.id(), paging);
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        follows.follow(me.id(), userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        follows.unfollow(me.id(), userId);
    }
}
