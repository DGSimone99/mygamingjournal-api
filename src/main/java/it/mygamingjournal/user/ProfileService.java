package it.mygamingjournal.user;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.library.LibraryStatsService;
import it.mygamingjournal.social.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final LibraryStatsService stats;
    private final FollowService follows;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User requireUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(long userId) {
        return MeResponse.from(requireUser(userId), stats.statsFor(userId));
    }

    @Transactional
    public MeResponse update(long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        if (request.languages() != null) {
            user.getLanguages().clear();
            user.getLanguages().addAll(request.languages());
        }
        if (request.gamingAccounts() != null) {
            user.setGamingAccounts(request.gamingAccounts().toEmbeddable());
        }
        return MeResponse.from(user, stats.statsFor(userId));
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /** Database cascades remove entries, progress, availability, reviews and follows. */
    @Transactional
    public void delete(long userId) {
        users.delete(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> search(String search, PageParams paging) {
        var pageable = paging.toPageable(Sort.by("username"));
        Page<User> page = search == null || search.isBlank()
                ? users.findAll(pageable)
                : users.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        search.trim(), search.trim(), pageable);
        return PageResponse.of(page, paging, UserSummary::from);
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(long userId, Long viewerId) {
        User user = requireUser(userId);
        Boolean followedByMe = viewerId == null ? null : follows.isFollowing(viewerId, userId);
        return PublicProfileResponse.from(user, stats.statsFor(userId), followedByMe);
    }
}
