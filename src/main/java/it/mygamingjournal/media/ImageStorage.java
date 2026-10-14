package it.mygamingjournal.media;

public interface ImageStorage {

    /** Stores the avatar (replacing any previous one) and returns its public HTTPS URL. */
    String uploadAvatar(long userId, byte[] content);
}
