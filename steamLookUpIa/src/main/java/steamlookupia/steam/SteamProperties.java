package steamlookupia.steam;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Classe plutôt qu'un record : aucun toString ne doit exposer la clé.
@ConfigurationProperties("steam")
public class SteamProperties {
    private String apiKey = "";
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
}
