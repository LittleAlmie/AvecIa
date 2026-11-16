package steamlookupia.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import steamlookupia.steam.SteamApiClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SteamIdentifierResolverTest {
    private final SteamApiClient client = mock(SteamApiClient.class);
    private final SteamIdentifierResolver resolver = new SteamIdentifierResolver(client);

    @ParameterizedTest
    @ValueSource(strings = {"76561198000000000", "https://steamcommunity.com/profiles/76561198000000000/",
            "http://www.steamcommunity.com/profiles/76561198000000000"})
    void resolvesNumericIdWithoutNetwork(String value) {
        assertEquals("76561198000000000", resolver.resolve(value));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"some-player", "https://steamcommunity.com/id/some-player/",
            "https://steamcommunity.com/id/some-player?l=french#profile"})
    void resolvesVanityName(String value) {
        when(client.resolveVanity("some-player")).thenReturn("76561198000000000");
        assertEquals("76561198000000000", resolver.resolve(value));
        verify(client).resolveVanity("some-player");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1234", "bad name", "--unknown", "https://example.com/id/name",
            "https://steamcommunity.com.evil.test/id/name", "https://steamcommunity.com/id/",
            "https://steamcommunity.com/id/name/extra", "https://steamcommunity.com/profiles/name",
            "ftp://steamcommunity.com/id/name", "https://user@steamcommunity.com/id/name",
            "https://steamcommunity.com:8443/id/name", "https://steamcommunity.com/id/a%2Fb"})
    void rejectsInvalidIdentifiersBeforeNetwork(String value) {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(value));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsNull() { assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null)); }
}
