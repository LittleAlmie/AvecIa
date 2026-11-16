package steamlookupia.steam;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import steamlookupia.model.Lookup.Game;
import steamlookupia.model.Lookup.Profile;
import steamlookupia.model.Lookup.Section;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

@Component
public class SteamApiClient {
    private final RestClient client;
    private final SteamProperties properties;

    public SteamApiClient(RestClient steamRestClient, SteamProperties properties) {
        this.client = steamRestClient;
        this.properties = properties;
    }

    public String resolveVanity(String name) {
        var response = get("/ISteamUser/ResolveVanityURL/v1/", Map.of("vanityurl", name));
        String id = text(response, "steamid");
        if (response.path("success").asInt(-1) != 1 || id == null || !id.matches("[0-9]{17}")) {
            throw new SteamException("Aucun compte Steam ne correspond à cet identifiant.");
        }
        return id;
    }

    public Profile profile(String id) {
        var players = get("/ISteamUser/GetPlayerSummaries/v2/", Map.of("steamids", id)).get("players");
        if (players == null || !players.isArray() || players.isEmpty() || !players.get(0).isObject()) {
            throw new SteamException("Steam n’a renvoyé aucun profil valide pour cet identifiant.");
        }
        var player = players.get(0);
        return new Profile(text(player, "personaname"), text(player, "profileurl"),
                player.path("communityvisibilitystate").asInt(-1) == 1);
    }

    public Section<List<Game>> ownedGames(String id) {
        var response = get("/IPlayerService/GetOwnedGames/v1/", Map.of(
                "steamid", id, "include_appinfo", "true", "include_played_free_games", "false"));
        return games(response, "game_count", false);
    }

    public Section<List<Game>> recentGames(String id) {
        var response = get("/IPlayerService/GetRecentlyPlayedGames/v1/", Map.of("steamid", id, "count", "0"));
        return games(response, "total_count", true);
    }

    private Section<List<Game>> games(JsonNode response, String countField, boolean recent) {
        var games = response.get("games");
        if (games == null || games.isNull()) {
            var count = optionalNumber(response, countField);
            if (count != null && count == 0) return Section.available(List.of());
            if (count != null && count > 0) throw invalidResponse();
            return Section.unavailable();
        }
        if (!games.isArray()) throw invalidResponse();
        List<Game> result = new ArrayList<>();
        for (var game : games) {
            Long appId = optionalNumber(game, "appid");
            if (appId == null || appId == 0) throw invalidResponse();
            result.add(new Game(appId, text(game, "name"), optionalNumber(game, "playtime_forever"),
                    recent ? optionalNumber(game, "playtime_2weeks") : null, date(game)));
        }
        return Section.available(List.copyOf(result));
    }

    private JsonNode get(String path, Map<String, String> params) {
        if (!properties.configured()) throw new SteamException("La clé Steam API est absente.");
        var uri = UriComponentsBuilder.fromUriString("https://api.steampowered.com")
                .path(path).queryParam("key", properties.getApiKey());
        params.forEach(uri::queryParam);
        try {
            var root = client.get().uri(uri.build().encode().toUri()).retrieve().body(JsonNode.class);
            if (root == null || !root.path("response").isObject()) throw invalidResponse();
            return root.path("response");
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new SteamException("Steam a refusé la clé API ou l’accès à cette requête.");
            }
            throw new SteamException("Erreur HTTP Steam (" + status + ").");
        } catch (RestClientException | JacksonException exception) {
            // Ne jamais propager le corps HTTP, l'URI ou la cause : ils peuvent contenir la clé.
            throw new SteamException("Consultation Steam impossible : réseau, délai dépassé ou réponse invalide.");
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static Long optionalNumber(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw invalidResponse();
        return value.longValue();
    }

    private static Instant date(JsonNode node) {
        Long epoch = optionalNumber(node, "rtime_last_played");
        if (epoch == null || epoch == 0) return null;
        try { return Instant.ofEpochSecond(epoch); }
        catch (DateTimeException exception) { throw invalidResponse(); }
    }

    private static SteamException invalidResponse() {
        return new SteamException("Steam a renvoyé une réponse vide, incomplète ou invalide.");
    }
}
