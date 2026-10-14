package it.mygamingjournal.catalog;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogConcurrencyTest {

    @Test
    void summariesAreFetchedConcurrentlyBeyondTheCarrierThreadCount() {
        RawgApi rawg = mock(RawgApi.class);
        int games = Runtime.getRuntime().availableProcessors() * 2 + 2;
        CountDownLatch allInFlight = new CountDownLatch(games);
        when(rawg.getGame(anyLong())).thenAnswer(invocation -> {
            allInFlight.countDown();
            // Every call must be in flight at once; serialized calls never reach zero.
            if (!allInFlight.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("RAWG calls were serialized");
            }
            long id = invocation.getArgument(0);
            return RawgFixtures.gameDetails(id, "Game " + id);
        });
        when(rawg.getScreenshots(anyLong())).thenAnswer(invocation -> RawgFixtures.screenshots(invocation.getArgument(0)));
        CatalogService service = new CatalogService(rawg, new RawgProperties("https://rawg.test/api", "test-key"));

        Map<Long, GameSummary> summaries = service.getSummaries(LongStream.rangeClosed(1, games).boxed().toList());

        assertThat(summaries).hasSize(games);
    }
}
