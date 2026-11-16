package steamlookupia.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import steamlookupia.model.Lookup.*;
import steamlookupia.steam.SteamApiClient;
import steamlookupia.steam.SteamException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SteamLookupServiceTest {
    private final SteamApiClient client = mock(SteamApiClient.class);
    private final SteamIdentifierResolver resolver = mock(SteamIdentifierResolver.class);
    private final Instant now = Instant.parse("2026-11-16T12:00:00Z");
    private final SteamLookupService service = new SteamLookupService(resolver, client, Clock.fixed(now, ZoneOffset.UTC));
    private static final String ID = "76561198000000000";

    @BeforeEach
    void setup() {
        when(resolver.resolve("alice")).thenReturn(ID);
        when(client.profile(ID)).thenReturn(new Profile("Alice", "url", false));
        when(client.ownedGames(ID)).thenReturn(Section.available(List.of()));
        when(client.recentGames(ID)).thenReturn(Section.available(List.of()));
    }

    @Test
    void keepsSuccessfulSectionsOnPartialFailure() {
        when(client.ownedGames(ID)).thenThrow(new SteamException("Erreur réseau."));
        var result = service.lookup("alice");
        assertTrue(result.hasErrors());
        assertEquals(Status.ERROR, result.library().status());
        assertEquals("Alice", result.profile().data().name());
        assertEquals(Status.AVAILABLE, result.recent().status());
    }

    @Test
    void privateProfileDoesNotHideAccessibleGamesOrCauseFailure() {
        when(client.profile(ID)).thenReturn(new Profile("Alice", "url", true));
        when(client.ownedGames(ID)).thenReturn(Section.available(List.of(game(1, 90L, now))));
        var result = service.lookup("alice");
        assertFalse(result.hasErrors());
        assertEquals(Status.PRIVATE, result.profile().status());
        assertEquals(90, result.library().data().totalMinutes());
    }

    @Test
    void calculatesLibraryStatisticsAndStableRanking() {
        var games = List.of(game(3, 0L, null), game(2, 120L, now), game(1, 120L, now));
        var library = new Library(games);
        assertEquals(2, library.playedCount());
        assertEquals(240, library.totalMinutes());
        assertEquals(List.of(1L, 2L, 3L), games.stream().sorted(SteamLookupService.MOST_PLAYED).map(Game::appId).toList());
        assertTrue(library.completePlaytimes());
        assertFalse(new Library(List.of(game(1, null, null))).completePlaytimes());
    }

    @Test
    void recentUsesLibraryDatesAndFiltersOldAndFutureGames() {
        var games = List.of(game(1, 30L, null), game(2, 30L, now.minusSeconds(3600)),
                game(3, 30L, now.minusSeconds(15 * 86400)), game(4, 30L, now.plusSeconds(1)),
                game(5, 30L, null), game(6, 30L, now.minusSeconds(14 * 86400)));
        var library = new Library(List.of(game(1, 30L, now)));
        var recent = service.selectRecent(games, library);
        assertEquals(List.of(1L, 2L, 6L, 5L), recent.stream().map(Game::appId).toList());
        assertEquals(now, recent.getFirst().lastPlayed());
        assertNull(recent.getLast().lastPlayed());
    }

    @Test
    void limitsRecentToFiveAndKeepsUnknownDatesWithoutLibrary() {
        var games = new ArrayList<Game>();
        for (int i = 8; i > 0; i--) games.add(game(i, 60L, null));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), service.selectRecent(games, null).stream().map(Game::appId).toList());
    }

    @Test
    void recentStillWorksWhenLibraryFails() {
        when(client.ownedGames(ID)).thenThrow(new SteamException("Erreur réseau."));
        when(client.recentGames(ID)).thenReturn(Section.available(List.of(game(1, 60L, null))));
        assertEquals(1, service.lookup("alice").recent().data().size());
    }

    private Game game(long id, Long minutes, Instant date) { return new Game(id, "Game", minutes, 30L, date); }
}
