package steamlookupia.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import steamlookupia.model.Lookup.*;
import steamlookupia.steam.SteamApiClient;
import steamlookupia.steam.SteamException;

@Service
public class SteamLookupService {
    public static final Comparator<Game> MOST_PLAYED = Comparator.comparing(Game::totalMinutes,
            Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(g -> g.name() == null ? "" : g.name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparingLong(Game::appId);
    private final SteamIdentifierResolver resolver;
    private final SteamApiClient client;
    private final Clock clock;

    public SteamLookupService(SteamIdentifierResolver resolver, SteamApiClient client, Clock clock) {
        this.resolver = resolver;
        this.client = client;
        this.clock = clock;
    }

    public Result lookup(String identifier) {
        String id = resolver.resolve(identifier);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var profileFuture = CompletableFuture.supplyAsync(() -> capture(() -> {
                Profile p = client.profile(id);
                return p.explicitlyPrivate() ? new Section<>(Status.PRIVATE, p, "Profil privé.") : Section.available(p);
            }), executor);
            var libraryFuture = CompletableFuture.supplyAsync(() -> capture(() -> {
                var section = client.ownedGames(id);
                return new Section<>(section.status(), section.data() == null ? null : new Library(section.data()), section.message());
            }), executor);
            var recentFuture = CompletableFuture.supplyAsync(() -> capture(() -> client.recentGames(id)), executor);
            var profile = profileFuture.join();
            var library = libraryFuture.join();
            var recent = recentFuture.join();
            if (recent.status() == Status.AVAILABLE) recent = Section.available(selectRecent(recent.data(), library.data()));
            return new Result(id, profile, library, recent);
        }
    }

    List<Game> selectRecent(List<Game> games, Library library) {
        Map<Long, Game> owned = library == null ? Map.of() : library.games().stream()
                .collect(Collectors.toMap(Game::appId, g -> g, (first, second) -> first));
        var now = clock.instant();
        var cutoff = now.minus(Duration.ofDays(14));
        return games.stream().map(g -> {
                    var match = owned.get(g.appId());
                    var date = g.lastPlayed() != null ? g.lastPlayed() : match == null ? null : match.lastPlayed();
                    return new Game(g.appId(), g.name(), g.totalMinutes(), g.recentMinutes(), date);
                })
                .filter(g -> g.lastPlayed() == null || (!g.lastPlayed().isBefore(cutoff) && !g.lastPlayed().isAfter(now)))
                .sorted(Comparator.comparing(Game::lastPlayed, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingLong(Game::appId))
                .limit(5).toList();
    }

    private static <T> Section<T> capture(Supplier<Section<T>> request) {
        try { return request.get(); }
        catch (SteamException exception) { return Section.error(exception.getMessage()); }
    }
}
