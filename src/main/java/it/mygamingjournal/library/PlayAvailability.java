package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The row existing means the user is available to play this game (until availableUntil, if set). */
@Entity
@Table(name = "play_availability")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayAvailability {

    @Id
    @Column(name = "entry_id")
    private Long entryId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> platforms = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> languages = new ArrayList<>();

    @Column(name = "available_until")
    private LocalDate availableUntil;

    public PlayAvailability(Long entryId) {
        this.entryId = entryId;
    }
}
