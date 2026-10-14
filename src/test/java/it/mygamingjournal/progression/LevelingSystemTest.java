package it.mygamingjournal.progression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelingSystemTest {

    private final LevelingSystem leveling = new LevelingSystem();

    @Test
    void experienceWeightsMatchV1() {
        // 3 games * 40 + 1 completed * 120 + 10 achievements * 15 + floor(12.9) hours * 5
        assertThat(leveling.calculateExp(3, 1, 10, 12.9)).isEqualTo(120 + 120 + 150 + 60);
    }

    @Test
    void levelIsOnePerThousandExperience() {
        assertThat(leveling.calculateLevel(999)).isZero();
        assertThat(leveling.calculateLevel(1000)).isEqualTo(1);
        assertThat(leveling.calculateLevel(2500)).isEqualTo(2);
    }
}
