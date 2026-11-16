package steamlookupia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import steamlookupia.steam.SteamProperties;

@SpringBootApplication
@EnableConfigurationProperties(SteamProperties.class)
public class SteamLookUpIaApplication {
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(SteamLookUpIaApplication.class, args)));
    }
}
