package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange
public interface RawgApi {

    @GetExchange("/games")
    RawgPage<RawgGame> listGames(@RequestParam Map<String, String> params);

    @GetExchange("/games/{id}")
    RawgGameDetails getGame(@PathVariable("id") long id);

    @GetExchange("/games/{id}/screenshots")
    RawgPage<RawgScreenshot> getScreenshots(@PathVariable("id") long id);

    @GetExchange("/games/{id}/achievements")
    RawgPage<RawgAchievement> getAchievements(@PathVariable("id") long id,
            @RequestParam("page") int page, @RequestParam("page_size") int pageSize);

    @GetExchange("/games/{id}/game-series")
    RawgPage<RawgGame> getSeries(@PathVariable("id") long id);

    @GetExchange("/games/{id}/additions")
    RawgPage<RawgGame> getAdditions(@PathVariable("id") long id);

    @GetExchange("/games/{id}/parent-games")
    RawgPage<RawgGame> getParentGames(@PathVariable("id") long id);

    @GetExchange("/genres")
    RawgPage<RawgNamed> getGenres(@RequestParam("page_size") int pageSize);

    @GetExchange("/platforms/lists/parents")
    RawgPage<RawgNamed> getParentPlatforms();
}
