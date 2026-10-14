package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A row exists only for unlocked achievements. Name, image etc. come from RAWG. */
@Entity
@Table(name = "achievement_progress")
@IdClass(AchievementProgressId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AchievementProgress {

    @Id
    @Column(name = "entry_id")
    private Long entryId;

    @Id
    @Column(name = "rawg_achievement_id")
    private Long rawgAchievementId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;
}
