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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final GameEntryRepository entries;
    private final AchievementProgressRepository progress;
    private final PlayAvailabilityRepository availabilities;
    private final CatalogService catalog;
    private final ProgressionService progression;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<LibraryEntryResponse> getLibrary(long userId, GameStatus status, LibrarySort sort,
                                                         PageParams paging) {
        var pageable = paging.toPageable(sort.toSort());
        Page<GameEntry> page = status == null
                ? entries.findByUserId(userId, pageable)
                : entries.findByUserIdAndStatus(userId, status, pageable);
        return new PageResponse<>(toResponses(page.getContent()), paging.page(), paging.pageSize(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<Long, GameStatus> getIndex(long userId) {
        return entries.findIndexByUserId(userId).stream()
                .collect(Collectors.toMap(GameEntryRepository.IndexRow::getRawgGameId,
                        GameEntryRepository.IndexRow::getStatus));
    }

    @Transactional
    public LibraryEntryResponse upsert(long userId, long rawgGameId, UpsertEntryRequest request) {
        Instant now = clock.instant();
        GameEntry entry = entries.findByUserIdAndRawgGameId(userId, rawgGameId).orElseGet(() -> {
            catalog.getGame(rawgGameId); // 404 if RAWG does not know this game
            return new GameEntry(userId, rawgGameId, now);
        });
        entry.setStatus(request.status());
        entry.setPersonalRating(request.personalRating());
        entry.setHoursPlayed(request.hoursPlayed() == null ? 0 : request.hoursPlayed());
        entry.setNotes(request.notes() == null ? "" : request.notes());
        entry.setUpdatedAt(now);
        entries.save(entry);
        progression.recalculate(userId);
        return toResponses(List.of(entry)).getFirst();
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

    private List<LibraryEntryResponse> toResponses(List<GameEntry> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> entryIds = page.stream().map(GameEntry::getId).toList();
        Map<Long, Long> unlocked = progress.countUnlockedByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(AchievementProgressRepository.UnlockedCountRow::getEntryId,
                        AchievementProgressRepository.UnlockedCountRow::getUnlocked));
        Map<Long, PlayAvailability> availability = availabilities.findAllById(entryIds).stream()
                .collect(Collectors.toMap(PlayAvailability::getEntryId, Function.identity()));
        Map<Long, GameSummary> games = catalog.getSummaries(page.stream().map(GameEntry::getRawgGameId).toList());
        return page.stream()
                .map(entry -> LibraryEntryResponse.of(entry, unlocked.getOrDefault(entry.getId(), 0L),
                        availability.get(entry.getId()), games.get(entry.getRawgGameId())))
                .toList();
    }
}
