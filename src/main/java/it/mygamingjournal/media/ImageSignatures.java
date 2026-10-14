package it.mygamingjournal.media;

/** Detects images by their magic bytes; the client-declared content type is not trusted. */
public final class ImageSignatures {

    private ImageSignatures() {
    }

    public static boolean isSupported(byte[] content) {
        return isJpeg(content) || isPng(content) || isWebp(content);
    }

    private static boolean isJpeg(byte[] c) {
        return c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] c) {
        return c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G'
                && c[4] == 0x0D && c[5] == 0x0A && c[6] == 0x1A && c[7] == 0x0A;
    }

    private static boolean isWebp(byte[] c) {
        return c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P';
    }
}
