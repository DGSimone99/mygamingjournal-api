package it.mygamingjournal.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameEntryRepository extends JpaRepository<GameEntry, Long> {

    Optional<GameEntry> findByUserIdAndRawgGameId(Long userId, long rawgGameId);

    Page<GameEntry> findByUserId(Long userId, Pageable pageable);

    Page<GameEntry> findByUserIdAndStatus(Long userId, GameStatus status, Pageable pageable);

    long countByRawgGameId(long rawgGameId);

    @Query("select e.rawgGameId as rawgGameId, e.status as status from GameEntry e where e.userId = :userId")
    List<IndexRow> findIndexByUserId(@Param("userId") Long userId);

    @Query("""
            select count(e) as totalGames,
                   coalesce(sum(case when e.status = it.mygamingjournal.library.GameStatus.COMPLETED then 1 else 0 end), 0) as completedGames,
                   coalesce(sum(case when e.status = it.mygamingjournal.library.GameStatus.WISHLIST then 1 else 0 end), 0) as wishlistGames,
                   coalesce(sum(e.hoursPlayed), 0.0) as hoursPlayed
            from GameEntry e
            where e.userId = :userId
            """)
    StatsRow statsForUser(@Param("userId") Long userId);

    interface IndexRow {
        Long getRawgGameId();

        GameStatus getStatus();
    }

    interface StatsRow {
        long getTotalGames();

        long getCompletedGames();

        long getWishlistGames();

        double getHoursPlayed();
    }
}
