package it.mygamingjournal.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Single entry point to RAWG. Maps RAWG JSON to our DTOs and keeps short-lived in-memory caches;
 * nothing coming from RAWG is ever persisted.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int ACHIEVEMENT_PAGE_SIZE = 40;
    private static final int MAX_ACHIEVEMENT_PAGES = 20;

    private final RawgApi rawg;
    private final RawgProperties properties;

    private final Cache<String, PageResponse<GameSummary>> searchCache = cache(Duration.ofMinutes(10), 500);
    private final Cache<Long, GameDetails> detailsCache = cache(Duration.ofHours(12), 2000);
    private final Cache<Long, List<GameAchievement>> achievementsCache = cache(Duration.ofHours(12), 500);
    private final Cache<Long, RelatedGames> relatedCache = cache(Duration.ofHours(12), 500);
    private final Cache<String, List<NamedItem>> taxonomyCache = cache(Duration.ofHours(24), 10);

    public CatalogService(RawgApi rawg, RawgProperties properties) {
        this.rawg = rawg;
        this.properties = properties;
    }

    public PageResponse<GameSummary> searchGames(GameQuery query, PageParams paging) {
        Map<String, String> params = query.toRawgParams(paging);
        return cached(searchCache, new TreeMap<>(params).toString(), () -> {
            try {
                RawgPage<RawgGame> page = call(() -> rawg.listGames(params));
                List<GameSummary> items = nullSafe(page.results()).stream().map(RawgMapper::toSummary).toList();
                return new PageResponse<>(items, paging.page(), paging.pageSize(), page.count());
            } catch (RawgNotFoundException e) {
                // RAWG answers 404 for a page past the last one.
                return new PageResponse<>(List.of(), paging.page(), paging.pageSize(), 0);
            }
        });
    }

    public GameDetails getGame(long id) {
        return cached(detailsCache, id, () -> {
            try {
                var game = call(() -> rawg.getGame(id));
                List<String> screenshots = nullSafe(call(() -> rawg.getScreenshots(id)).results()).stream()
                        .map(RawgScreenshot::image)
                        .toList();
                return RawgMapper.toDetails(game, screenshots);
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public List<GameAchievement> getAchievements(long id) {
        return cached(achievementsCache, id, () -> {
            try {
                List<GameAchievement> all = new ArrayList<>();
                for (int page = 1; page <= MAX_ACHIEVEMENT_PAGES; page++) {
                    int current = page;
                    RawgPage<RawgAchievement> result = call(() -> rawg.getAchievements(id, current, ACHIEVEMENT_PAGE_SIZE));
                    nullSafe(result.results()).forEach(achievement -> all.add(RawgMapper.toAchievement(achievement)));
                    if (result.next() == null) {
                        break;
                    }
                }
                return List.copyOf(all);
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public RelatedGames getRelated(long id) {
        return cached(relatedCache, id, () -> {
            try {
                return new RelatedGames(
                        summaries(call(() -> rawg.getSeries(id))),
                        summaries(call(() -> rawg.getAdditions(id))),
                        summaries(call(() -> rawg.getParentGames(id))));
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public List<NamedItem> getGenres() {
        return cached(taxonomyCache, "genres", () ->
                nullSafe(call(() -> rawg.getGenres(40)).results()).stream().map(RawgMapper::toNamed).toList());
    }

    public List<NamedItem> getPlatforms() {
        return cached(taxonomyCache, "platforms", () ->
                nullSafe(call(rawg::getParentPlatforms).results()).stream().map(RawgMapper::toNamed).toList());
    }

    /**
     * Loads summaries in parallel (virtual threads, through the details cache).
     * Games that cannot be loaded are simply missing from the result.
     */
    public Map<Long, GameSummary> getSummaries(Collection<Long> ids) {
        Map<Long, GameSummary> result = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Long id : Set.copyOf(ids)) {
                executor.submit(() -> {
                    try {
                        result.put(id, getGame(id).toSummary());
                    } catch (RuntimeException e) {
                        log.debug("Game {} could not be loaded from RAWG: {}", id, e.getClass().getSimpleName());
                    }
                });
            }
        }
        return result;
    }

    public void clearCaches() {
        searchCache.invalidateAll();
        detailsCache.invalidateAll();
        achievementsCache.invalidateAll();
        relatedCache.invalidateAll();
        taxonomyCache.invalidateAll();
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.hasApiKey()) {
            throw new RawgUnavailableException("RAWG API key not configured");
        }
        // Never log exception messages here: they contain the request URL, which contains the key.
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw new RawgNotFoundException();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("RAWG rejected the API key (status {})", e.getStatusCode().value());
            throw new RawgUnavailableException("RAWG API key is invalid");
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("RAWG rate limit reached");
            throw new RawgUnavailableException("RAWG rate limit reached, try again later");
        } catch (RestClientResponseException e) {
            log.warn("RAWG answered with status {}", e.getStatusCode().value());
            throw new RawgUpstreamException("RAWG request failed");
        } catch (ResourceAccessException e) {
            log.warn("RAWG not reachable ({})", e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
            throw new RawgUpstreamException("RAWG is not reachable");
        }
    }

    private static List<GameSummary> summaries(RawgPage<RawgGame> page) {
        return nullSafe(page.results()).stream().map(RawgMapper::toSummary).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static NotFoundException gameNotFound(long id) {
        return new NotFoundException("Game " + id + " not found");
    }

    /**
     * Read-through without Cache.get(key, loader): that runs the loader inside ConcurrentHashMap.compute, whose
     * monitor pins virtual threads during the blocking HTTP call (JDK 21) and serializes parallel enrichment.
     * Two concurrent misses may both call RAWG; that is cheaper than the pinning.
     */
    private static <K, V> V cached(Cache<K, V> cache, K key, Supplier<V> loader) {
        V hit = cache.getIfPresent(key);
        if (hit != null) {
            return hit;
        }
        V value = loader.get();
        cache.put(key, value);
        return value;
    }

    private static <K, V> Cache<K, V> cache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }

    /** RAWG 404, translated by each caller into what it means for that resource. */
    private static final class RawgNotFoundException extends RuntimeException {
    }
}
