package it.mygamingjournal.library;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AchievementProgressId implements Serializable {
    private Long entryId;
    private Long rawgAchievementId;
}
