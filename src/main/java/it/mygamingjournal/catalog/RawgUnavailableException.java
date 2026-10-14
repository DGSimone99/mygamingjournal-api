package it.mygamingjournal.catalog;

/** RAWG cannot be used right now: missing or rejected key, or rate limited. Mapped to 503. */
public class RawgUnavailableException extends RuntimeException {
    public RawgUnavailableException(String message) {
        super(message);
    }
}
