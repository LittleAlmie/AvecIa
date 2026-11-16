package steamlookupia.steam;

import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import steamlookupia.model.Lookup.Status;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SteamApiClientTest {
    private MockRestServiceServer server;
    private SteamApiClient client;
    private SteamProperties properties;
    private static final String SECRET = "secret-do-not-print";
    private static final String ID = "76561198000000000";

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new SteamProperties();
        properties.setApiKey(SECRET);
        client = new SteamApiClient(builder.build(), properties);
    }

    @Test
    void resolvesVanity() {
        server.expect(queryParam("vanityurl", "name")).andExpect(queryParam("key", SECRET))
                .andRespond(withSuccess("{\"response\":{\"success\":1,\"steamid\":\"" + ID + "\"}}", MediaType.APPLICATION_JSON));
        assertEquals(ID, client.resolveVanity("name"));
        server.verify();
    }

    @Test
    void unknownVanityIsAnError() {
        respond("{\"response\":{\"success\":42}}");
        assertThrows(SteamException.class, () -> client.resolveVanity("unknown"));
    }

    @Test
    void parsesPrivateProfile() {
        respond("{\"response\":{\"players\":[{\"personaname\":\"Alice\",\"profileurl\":\"https://steamcommunity.com/id/alice\",\"communityvisibilitystate\":1}]}}");
        var profile = client.profile(ID);
        assertEquals("Alice", profile.name());
        assertTrue(profile.explicitlyPrivate());
    }

    @Test
    void requestsOnlyOwnedGamesAndKeepsUnknownValues() {
        server.expect(queryParam("include_played_free_games", "false"))
                .andExpect(queryParam("include_appinfo", "true"))
                .andRespond(withSuccess("{\"response\":{\"game_count\":1,\"games\":[{\"appid\":10,\"name\":\"Game\"}]}}", MediaType.APPLICATION_JSON));
        var games = client.ownedGames(ID).data();
        assertEquals(1, games.size());
        assertNull(games.getFirst().totalMinutes());
        assertNull(games.getFirst().lastPlayed());
    }

    @Test
    void missingGamesDoesNotProvePrivacy() {
        respond("{\"response\":{}}");
        assertEquals(Status.UNAVAILABLE, client.ownedGames(ID).status());
    }

    @Test
    void explicitZeroIsAnEmptyLibrary() {
        respond("{\"response\":{\"game_count\":0}}");
        assertEquals(List.of(), client.ownedGames(ID).data());
    }

    @Test
    void recentZeroIsAnEmptyActivity() {
        server.expect(queryParam("count", "0"))
                .andRespond(withSuccess("{\"response\":{\"total_count\":0}}", MediaType.APPLICATION_JSON));
        assertEquals(List.of(), client.recentGames(ID).data());
    }

    @Test
    void missingRecentDataIsUnavailable() {
        respond("{\"response\":{}}");
        assertEquals(Status.UNAVAILABLE, client.recentGames(ID).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "{}", "{\"response\":[]}",
            "{\"response\":{\"game_count\":2}}", "{\"response\":{\"games\":false}}",
            "{\"response\":{\"games\":[{\"appid\":10,\"playtime_forever\":-1}]}}",
            "{\"response\":{\"games\":[{\"appid\":10,\"playtime_forever\":1.5}]}}",
            "{\"response\":{\"games\":[{}]}}"})
    void invalidResponsesAreSafeErrors(String body) {
        respond(body);
        var exception = assertThrows(SteamException.class, () -> client.ownedGames(ID));
        assertFalse(exception.toString().contains(SECRET));
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500})
    void handlesHttpErrorsWithoutLeakingSecret(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body(SECRET));
        var exception = assertThrows(SteamException.class, () -> client.profile(ID));
        assertFalse(exception.toString().contains(SECRET));
        assertNull(exception.getCause());
    }

    @Test
    void handlesTimeoutWithoutLeakingRequestUri() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("key=" + SECRET)));
        var exception = assertThrows(SteamException.class, () -> client.profile(ID));
        assertFalse(exception.toString().contains(SECRET));
        assertNull(exception.getCause());
    }

    @Test
    void missingKeyMakesNoRequest() {
        properties.setApiKey(" ");
        assertThrows(SteamException.class, () -> client.profile(ID));
        server.verify();
    }

    private void respond(String json) {
        server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
