package it.mygamingjournal.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    Page<Follow> findByFollowerId(Long followerId, Pageable pageable);
}
