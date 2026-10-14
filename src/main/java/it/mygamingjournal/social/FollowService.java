package it.mygamingjournal.social;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository follows;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public void follow(long followerId, long followedId) {
        if (followerId == followedId) {
            throw new BadRequestException("You cannot follow yourself");
        }
        if (!users.existsById(followedId)) {
            throw new NotFoundException("User not found");
        }
        FollowId id = new FollowId(followerId, followedId);
        if (!follows.existsById(id)) {
            follows.save(new Follow(followerId, followedId, clock.instant()));
        }
    }

    @Transactional
    public void unfollow(long followerId, long followedId) {
        follows.deleteById(new FollowId(followerId, followedId));
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(long followerId, long followedId) {
        return follows.existsById(new FollowId(followerId, followedId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> following(long followerId, PageParams paging) {
        Page<Follow> page = follows.findByFollowerId(followerId,
                paging.toPageable(Sort.by(Sort.Order.desc("createdAt"))));
        Map<Long, User> byId = users.findAllById(page.getContent().stream().map(Follow::getFollowedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        var items = page.getContent().stream()
                .map(follow -> byId.get(follow.getFollowedId()))
                .filter(Objects::nonNull)
                .map(UserSummary::from)
                .toList();
        return new PageResponse<>(items, paging.page(), paging.pageSize(), page.getTotalElements());
    }
}
