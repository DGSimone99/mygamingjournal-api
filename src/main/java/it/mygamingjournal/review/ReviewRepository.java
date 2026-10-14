package it.mygamingjournal.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndRawgGameId(Long userId, long rawgGameId);

    Page<Review> findByRawgGameId(long rawgGameId, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    long deleteByUserIdAndRawgGameId(Long userId, long rawgGameId);

    @Query("select avg(r.score) as averageScore, count(r) as reviewCount from Review r where r.rawgGameId = :rawgGameId")
    StatsRow statsForGame(@Param("rawgGameId") long rawgGameId);

    interface StatsRow {
        Double getAverageScore();

        long getReviewCount();
    }
}
