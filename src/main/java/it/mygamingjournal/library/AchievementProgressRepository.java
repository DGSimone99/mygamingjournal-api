package it.mygamingjournal.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AchievementProgressRepository extends JpaRepository<AchievementProgress, AchievementProgressId> {

    List<AchievementProgress> findByEntryId(Long entryId);

    @Query("""
            select p.entryId as entryId, count(p) as unlocked
            from AchievementProgress p
            where p.entryId in :entryIds
            group by p.entryId
            """)
    List<UnlockedCountRow> countUnlockedByEntryIds(@Param("entryIds") Collection<Long> entryIds);

    @Query("""
            select count(p)
            from AchievementProgress p, GameEntry e
            where e.id = p.entryId and e.userId = :userId
            """)
    long countUnlockedByUserId(@Param("userId") Long userId);

    interface UnlockedCountRow {
        Long getEntryId();

        Long getUnlocked();
    }
}
