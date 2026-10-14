package it.mygamingjournal.catalog;

import java.util.List;

public record RelatedGames(List<GameSummary> series, List<GameSummary> additions, List<GameSummary> parents) {
}
