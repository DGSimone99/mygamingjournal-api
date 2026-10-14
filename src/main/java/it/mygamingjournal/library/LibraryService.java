package it.mygamingjournal.library;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.progression.ProgressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RAWG is never called inside a database transaction: a slow RAWG must not keep pooled connections busy.
 * Entries are loaded in a short transaction, then enriched with game data outside it.
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final GameEntryRepository entries;
    private final AchievementProgressRepository progress;
    private final PlayAvailabilityRepository availabilities;
    private final CatalogService catalog;
    private final ProgressionService progression;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PageResponse<LibraryEntryResponse> getLibrary(long userId, GameStatus status, LibrarySort sort,
                                                         PageParams paging) {
        var pageable = paging.toPageable(sort.toSort());
        LoadedEntries loaded = transaction.execute(tx -> {
            Page<GameEntry> page = status == null
                    ? entries.findByUserId(userId, pageable)
                    : entries.findByUserIdAndStatus(userId, status, pageable);
            return load(page.getContent(), page.getTotalElements());
        });
        return new PageResponse<>(enrich(loaded), paging.page(), paging.pageSize(), loaded.total());
    }

    @Transactional(readOnly = true)
    public Map<Long, GameStatus> getIndex(long userId) {
        return entries.findIndexByUserId(userId).stream()
                .collect(Collectors.toMap(GameEntryRepository.IndexRow::getRawgGameId,
                        GameEntryRepository.IndexRow::getStatus));
    }

    public LibraryEntryResponse upsert(long userId, long rawgGameId, UpsertEntryRequest request) {
        if (entries.findByUserIdAndRawgGameId(userId, rawgGameId).isEmpty()) {
            catalog.getGame(rawgGameId); // 404 if RAWG does not know this game
        }
        LoadedEntries loaded = transaction.execute(tx -> {
            Instant now = clock.instant();
            GameEntry entry = entries.findByUserIdAndRawgGameId(userId, rawgGameId)
                    .orElseGet(() -> new GameEntry(userId, rawgGameId, now));
            entry.setStatus(request.status());
            entry.setPersonalRating(request.personalRating());
            entry.setHoursPlayed(request.hoursPlayed() == null ? 0 : request.hoursPlayed());
            entry.setNotes(request.notes() == null ? "" : request.notes());
            entry.setUpdatedAt(now);
            entries.save(entry);
            progression.recalculate(userId);
            return load(List.of(entry), 1);
        });
        return enrich(loaded).getFirst();
    }

    @Transactional
    public void delete(long userId, long rawgGameId) {
        entries.delete(requireEntry(userId, rawgGameId));
        progression.recalculate(userId);
    }

    @Transactional(readOnly = true)
    public GameEntry requireEntry(long userId, long rawgGameId) {
        return entries.findByUserIdAndRawgGameId(userId, rawgGameId)
                .orElseThrow(() -> new NotFoundException("Game is not in your library"));
    }

    private LoadedEntries load(List<GameEntry> page, long total) {
        if (page.isEmpty()) {
            return new LoadedEntries(List.of(), Map.of(), Map.of(), total);
        }
        List<Long> entryIds = page.stream().map(GameEntry::getId).toList();
        Map<Long, Long> unlocked = progress.countUnlockedByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(AchievementProgressRepository.UnlockedCountRow::getEntryId,
                        AchievementProgressRepository.UnlockedCountRow::getUnlocked));
        Map<Long, PlayAvailability> availability = availabilities.findAllById(entryIds).stream()
                .collect(Collectors.toMap(PlayAvailability::getEntryId, Function.identity()));
        return new LoadedEntries(page, unlocked, availability, total);
    }

    private List<LibraryEntryResponse> enrich(LoadedEntries loaded) {
        if (loaded.entries().isEmpty()) {
            return List.of();
        }
        Map<Long, GameSummary> games = catalog.getSummaries(
                loaded.entries().stream().map(GameEntry::getRawgGameId).toList());
        return loaded.entries().stream()
                .map(entry -> LibraryEntryResponse.of(entry, loaded.unlocked().getOrDefault(entry.getId(), 0L),
                        loaded.availability().get(entry.getId()), games.get(entry.getRawgGameId())))
                .toList();
    }

    private record LoadedEntries(List<GameEntry> entries, Map<Long, Long> unlocked,
                                 Map<Long, PlayAvailability> availability, long total) {
    }
}
