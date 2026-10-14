package it.mygamingjournal.user;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.library.GameStatus;
import it.mygamingjournal.library.LibraryEntryResponse;
import it.mygamingjournal.library.LibraryService;
import it.mygamingjournal.library.LibrarySort;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profiles;
    private final LibraryService library;

    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal AuthUser me) {
        return profiles.getMe(me.id());
    }

    @PatchMapping("/me")
    public MeResponse updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(me.id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(me.id(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AuthUser me) {
        profiles.delete(me.id());
    }

    @GetMapping("/users")
    public PageResponse<UserSummary> searchUsers(@RequestParam(required = false) @Size(max = 50) String search,
            @Valid @ParameterObject PageParams paging) {
        return profiles.search(search, paging);
    }

    @GetMapping("/users/{userId}")
    public PublicProfileResponse getUser(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        return profiles.getPublicProfile(userId, me == null ? null : me.id());
    }

    @GetMapping("/users/{userId}/library")
    public PageResponse<LibraryEntryResponse> getUserLibrary(@PathVariable @Positive long userId,
            @RequestParam(required = false) GameStatus status,
            @RequestParam(required = false) String sort,
            @Valid @ParameterObject PageParams paging) {
        profiles.requireUser(userId);
        return library.getLibrary(userId, status, LibrarySort.fromParam(sort), paging);
    }
}
