package it.mygamingjournal.library;

import it.mygamingjournal.auth.AuthUser;
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

import java.util.List;

@RestController
@RequestMapping("/api/me/library/{rawgGameId}/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievements;

    @GetMapping
    public List<AchievementProgressResponse> list(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId) {
        return achievements.list(me.id(), rawgGameId);
    }

    @PutMapping("/{achievementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @PathVariable @Positive long achievementId) {
        achievements.unlock(me.id(), rawgGameId, achievementId);
    }

    @DeleteMapping("/{achievementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @PathVariable @Positive long achievementId) {
        achievements.lock(me.id(), rawgGameId, achievementId);
    }
}
