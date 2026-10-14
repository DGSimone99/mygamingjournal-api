package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "game_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "rawg_game_id", nullable = false, updatable = false)
    private long rawgGameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Column(name = "personal_rating")
    private Double personalRating;

    @Column(name = "hours_played", nullable = false)
    private double hoursPlayed;

    @Column(nullable = false)
    private String notes;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameEntry(Long userId, long rawgGameId, Instant now) {
        this.userId = userId;
        this.rawgGameId = rawgGameId;
        this.status = GameStatus.BACKLOG;
        this.notes = "";
        this.addedAt = now;
        this.updatedAt = now;
    }
}
