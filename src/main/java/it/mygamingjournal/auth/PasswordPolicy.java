package it.mygamingjournal.auth;

import it.mygamingjournal.common.error.BadRequestException;

import java.nio.charset.StandardCharsets;

/** BCrypt rejects passwords over 72 bytes; @Size counts characters, so accented passwords need this check too. */
public final class PasswordPolicy {

    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void requireBcryptCompatible(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("Password must be at most 72 bytes");
        }
    }
}
