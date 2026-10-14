package it.mygamingjournal.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryImageStorageTest {

    @Test
    void missingConfigurationIsUnavailable() {
        CloudinaryImageStorage storage = new CloudinaryImageStorage("");

        assertThatThrownBy(() -> storage.uploadAvatar(1L, new byte[]{1}))
                .isInstanceOf(MediaUnavailableException.class)
                .hasMessage("Image upload is not configured");
    }
}
