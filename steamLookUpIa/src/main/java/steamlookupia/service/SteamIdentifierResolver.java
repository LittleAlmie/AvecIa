package steamlookupia.service;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;
import steamlookupia.steam.SteamApiClient;

@Component
public class SteamIdentifierResolver {
    private final SteamApiClient client;
    public SteamIdentifierResolver(SteamApiClient client) { this.client = client; }

    public String resolve(String input) {
        String value = normalize(input);
        return value.matches("[0-9]{17}") ? value : client.resolveVanity(value);
    }

    // Validation séparée : les erreurs d'utilisation sont détectées avant tout appel réseau.
    public String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (value.contains("://")) {
            try {
                URI uri = new URI(value);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || !("steamcommunity.com".equalsIgnoreCase(uri.getHost())
                        || "www.steamcommunity.com".equalsIgnoreCase(uri.getHost()))
                        || uri.getUserInfo() != null || uri.getPort() != -1) throw invalid();
                String path = uri.getPath();
                if (path == null) throw invalid();
                String[] parts = path.split("/", -1);
                if (!(parts.length == 3 || (parts.length == 4 && parts[3].isEmpty()))) throw invalid();
                if (parts[1].equals("profiles") && parts[2].matches("[0-9]{17}")) return parts[2];
                if (!parts[1].equals("id")) throw invalid();
                value = parts[2];
            } catch (URISyntaxException exception) { throw invalid(); }
        }
        if (value.isBlank() || !value.matches("[A-Za-z0-9_-]+") || value.startsWith("-")
                || (value.matches("[0-9]+") && !value.matches("[0-9]{17}"))) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Identifiant Steam invalide : utilisez un SteamID64, un nom personnalisé ou une URL de profil Steam.");
    }
}
