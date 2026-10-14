package it.mygamingjournal.catalog;

import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.review.CommunityStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;
    private final CommunityStatsService communityStats;

    @GetMapping("/games")
    public PageResponse<GameSummary> searchGames(@Valid GameQuery query, @Valid PageParams paging) {
        return catalog.searchGames(query, paging);
    }

    @GetMapping("/games/{rawgGameId}")
    public GameDetails getGame(@PathVariable @Positive long rawgGameId) {
        return catalog.getGame(rawgGameId).withCommunity(communityStats.forGame(rawgGameId));
    }

    @GetMapping("/games/{rawgGameId}/achievements")
    public List<GameAchievement> getAchievements(@PathVariable @Positive long rawgGameId) {
        return catalog.getAchievements(rawgGameId);
    }

    @GetMapping("/games/{rawgGameId}/related")
    public RelatedGames getRelated(@PathVariable @Positive long rawgGameId) {
        return catalog.getRelated(rawgGameId);
    }

    @GetMapping("/genres")
    public List<NamedItem> getGenres() {
        return catalog.getGenres();
    }

    @GetMapping("/platforms")
    public List<NamedItem> getPlatforms() {
        return catalog.getPlatforms();
    }
}
