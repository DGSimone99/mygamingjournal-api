package it.mygamingjournal.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignaturesTest {

    @Test
    void recognisesJpegPngAndWebp() {
        assertThat(ImageSignatures.isSupported(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isTrue();
        assertThat(ImageSignatures.isSupported(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isTrue();
        assertThat(ImageSignatures.isSupported("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageSignatures.isSupported("hello world".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(ImageSignatures.isSupported("GIF89a".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageSignatures.isSupported(new byte[0])).isFalse();
    }
}
