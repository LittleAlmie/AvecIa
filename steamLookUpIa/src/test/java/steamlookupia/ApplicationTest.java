package steamlookupia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import steamlookupia.cli.SteamCommand;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationTest {
    @Test
    void contextStartsInCliModeAndHelpNeedsNoSecret() {
        try (var context = SpringApplication.run(SteamLookUpIaApplication.class, "--help")) {
            assertEquals(0, context.getBean(SteamCommand.class).getExitCode());
            assertFalse(context.containsBean("dispatcherServlet"));
        }
    }
}
