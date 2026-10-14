package it.mygamingjournal.catalog;

public record CommunityStats(Double averageScore, long reviewCount, long libraryCount) {

    public static final CommunityStats EMPTY = new CommunityStats(null, 0, 0);
}
