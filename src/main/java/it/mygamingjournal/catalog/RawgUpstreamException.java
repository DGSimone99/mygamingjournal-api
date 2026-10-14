package it.mygamingjournal.catalog;

/** RAWG failed or did not answer in time. Mapped to 502. */
public class RawgUpstreamException extends RuntimeException {
    public RawgUpstreamException(String message) {
        super(message);
    }
}
