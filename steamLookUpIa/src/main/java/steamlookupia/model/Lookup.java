package steamlookupia.model;

import java.time.Instant;
import java.util.List;

public final class Lookup {
    private Lookup() {}

    public enum Status { AVAILABLE, PRIVATE, UNAVAILABLE, ERROR }
    public record Section<T>(Status status, T data, String message) {
        public static <T> Section<T> available(T data) {
            return new Section<>(Status.AVAILABLE, data, null);
        }
        public static <T> Section<T> unavailable() {
            return new Section<>(Status.UNAVAILABLE, null, "Données absentes ou masquées.");
        }
        public static <T> Section<T> error(String message) {
            return new Section<>(Status.ERROR, null, message);
        }
    }
    public record Profile(String name, String url, boolean explicitlyPrivate) {}
    public record Game(long appId, String name, Long totalMinutes, Long recentMinutes, Instant lastPlayed) {}
    public record Library(List<Game> games) {
        public Library { games = List.copyOf(games); }
        public long playedCount() {
            return games.stream().filter(g -> g.totalMinutes() != null && g.totalMinutes() > 0).count();
        }
        public boolean completePlaytimes() {
            return games.stream().allMatch(g -> g.totalMinutes() != null);
        }
        public long totalMinutes() {
            return games.stream().filter(g -> g.totalMinutes() != null).mapToLong(Game::totalMinutes).sum();
        }
    }
    public record Result(String steamId, Section<Profile> profile, Section<Library> library,
                         Section<List<Game>> recent) {
        public boolean hasErrors() {
            return profile.status() == Status.ERROR || library.status() == Status.ERROR
                    || recent.status() == Status.ERROR;
        }
    }
}
