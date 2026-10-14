package it.mygamingjournal.library;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService library;
    private final AvailabilityService availability;

    @GetMapping
    public PageResponse<LibraryEntryResponse> getLibrary(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) GameStatus status,
            @RequestParam(required = false) String sort,
            @Valid PageParams paging) {
        return library.getLibrary(me.id(), status, LibrarySort.fromParam(sort), paging);
    }

    @GetMapping("/index")
    public Map<Long, GameStatus> getIndex(@AuthenticationPrincipal AuthUser me) {
        return library.getIndex(me.id());
    }

    @PutMapping("/{rawgGameId}")
    public LibraryEntryResponse upsert(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @Valid @RequestBody UpsertEntryRequest request) {
        return library.upsert(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/{rawgGameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        library.delete(me.id(), rawgGameId);
    }

    @PutMapping("/{rawgGameId}/availability")
    public AvailabilityResponse setAvailability(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId, @Valid @RequestBody AvailabilityRequest request) {
        return availability.set(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/{rawgGameId}/availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAvailability(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        availability.clear(me.id(), rawgGameId);
    }
}
