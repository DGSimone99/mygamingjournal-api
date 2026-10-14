package it.mygamingjournal.matchmaking;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmaking;

    @GetMapping("/api/games/{rawgGameId}/available-players")
    public PageResponse<AvailablePlayerResponse> availablePlayers(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z0-9-]+$") String platform,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}$") String language,
            @Valid PageParams paging) {
        return matchmaking.findAvailablePlayers(me.id(), rawgGameId, platform, language, paging);
    }
}
