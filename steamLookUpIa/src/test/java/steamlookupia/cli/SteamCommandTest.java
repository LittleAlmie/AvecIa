package steamlookupia.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import steamlookupia.model.Lookup.*;
import steamlookupia.service.SteamIdentifierResolver;
import steamlookupia.service.SteamLookupService;
import steamlookupia.steam.SteamApiClient;
import steamlookupia.steam.SteamException;
import steamlookupia.steam.SteamProperties;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SteamCommandTest {
    private final SteamLookupService service = mock(SteamLookupService.class);
    private final SteamApiClient client = mock(SteamApiClient.class);
    private final SteamProperties properties = new SteamProperties();
    private final StringWriter output = new StringWriter();
    private final StringWriter errors = new StringWriter();
    private final SteamCommand command = new SteamCommand(service, new SteamIdentifierResolver(client), properties, new SteamFormatter());
    private static final String ID = "76561198000000000";

    @BeforeEach
    void setup() { properties.setApiKey("never-disclose-this-key"); }

    @Test
    void helpWorksWithoutKeyOrNetwork() {
        properties.setApiKey("");
        assertEquals(0, run("--help"));
        assertTrue(output.toString().contains("Usage"));
        assertEquals("", errors.toString());
        verifyNoInteractions(service, client);
    }

    @Test
    void invalidArgumentCountAndIdentifierReturnTwo() {
        assertEquals(2, run());
        assertEquals(2, run(ID, "extra"));
        assertEquals(2, run("--bad-option"));
        verifyNoInteractions(service, client);
    }

    @Test
    void missingKeyProvidesConfigurationInstructions() {
        properties.setApiKey("");
        assertEquals(2, run(ID));
        assertTrue(errors.toString().contains("https://steamcommunity.com/dev/apikey"));
        assertTrue(errors.toString().contains(".env"));
        verifyNoInteractions(service, client);
    }

    @Test
    void successfulLookupDisplaysThreeSectionsAndFrenchUnits() {
        var games = List.of(new Game(1, "Portal", 90L, 30L, Instant.parse("2026-11-16T10:00:00Z")));
        when(service.lookup(ID)).thenReturn(result(Section.available(new Library(games)), Section.available(games)));
        assertEquals(0, run(ID));
        var text = output.toString();
        assertTrue(text.contains("── Profil"));
        assertTrue(text.contains("── Bibliothèque"));
        assertTrue(text.contains("── Activité récente"));
        assertTrue(text.contains("1,5 h"));
        assertTrue(text.contains("0,5 h"));
        assertTrue(text.contains("2026-11-16"));
        assertFalse(text.contains(properties.getApiKey()));
        assertEquals("", errors.toString());
    }

    @Test
    void partialFailureHasNonzeroExitAndPreservesOutput() {
        when(service.lookup(ID)).thenReturn(result(Section.error("Erreur réseau."), Section.available(List.of())));
        assertEquals(1, run(ID));
        assertTrue(output.toString().contains("Alice"));
        assertTrue(errors.toString().contains("Bibliothèque : Erreur réseau."));
    }

    @Test
    void privateAndUnavailableSectionsAreSuccessful() {
        when(service.lookup(ID)).thenReturn(new Result(ID,
                new Section<>(Status.PRIVATE, new Profile("Alice", null, true), "Profil privé."),
                Section.unavailable(), Section.unavailable()));
        assertEquals(0, run(ID));
        assertTrue(output.toString().contains("Données absentes ou masquées."));
        assertEquals("", errors.toString());
    }

    @Test
    void resolutionFailureIsReportedWithoutStackTrace() {
        when(service.lookup(ID)).thenThrow(new SteamException("Compte introuvable."));
        assertEquals(1, run(ID));
        assertEquals("Compte introuvable." + System.lineSeparator(), errors.toString());
    }

    @Test
    void topTenIsLimitedAndMissingTimesAreNotInvented() {
        var games = new ArrayList<Game>();
        for (int i = 12; i > 0; i--) games.add(new Game(i, "Game " + i, (long) i * 60, null, null));
        games.add(new Game(13, "Unknown", null, null, null));
        when(service.lookup(ID)).thenReturn(result(Section.available(new Library(games)), Section.available(List.of())));
        assertEquals(0, run(ID));
        var text = output.toString();
        assertTrue(text.contains("Jeux possédés : 13"));
        assertTrue(text.contains("Temps total : Indisponible"));
        assertTrue(text.lines().anyMatch(line -> line.matches(".*│ 10 *│ Game 3 .*")));
        assertFalse(text.lines().anyMatch(line -> line.matches(".*│ 11 *│.*")));
        assertFalse(text.lines().anyMatch(line -> line.matches(".*│ Game 2 *│.*")));
    }

    @Test
    void removesTerminalControlsFromExternalFields() {
        assertEquals("A [31mB C", SteamFormatter.safe("A\u001b[31mB\nC"));
        assertEquals("Indisponible", SteamFormatter.safe(null));
    }

    @Test
    void longUnicodeNamesKeepTableBordersAlignedAndNoAnsiInRedirectedOutput() {
        var games = List.of(new Game(1, "Très long nom de jeu " + "🎮".repeat(40), 60L, 10L, null));
        when(service.lookup(ID)).thenReturn(result(Section.available(new Library(games)), Section.available(games)));
        assertEquals(0, run(ID));
        var text = output.toString();
        assertTrue(text.contains("…"));
        assertFalse(text.contains("\u001b"));
        var table = text.lines().filter(line -> line.startsWith("  │") || line.startsWith("  ┌")
                || line.startsWith("  ├") || line.startsWith("  └")).toList();
        int width = table.getFirst().codePointCount(0, table.getFirst().length());
        // La deuxième table a une colonne de moins : vérifier séparément la première.
        int end = 0;
        while (!table.get(end).startsWith("  └")) end++;
        for (var line : table.subList(0, end + 1)) assertEquals(width, line.codePointCount(0, line.length()));
    }

    private Result result(Section<Library> library, Section<List<Game>> recent) {
        return new Result(ID, Section.available(new Profile("Alice", "https://steamcommunity.com/id/alice/", false)), library, recent);
    }
    private int run(String... args) { return command.execute(args, new PrintWriter(output, true), new PrintWriter(errors, true)); }
}
