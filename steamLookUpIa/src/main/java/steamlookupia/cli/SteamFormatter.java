package steamlookupia.cli;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import steamlookupia.model.Lookup.*;
import steamlookupia.service.SteamLookupService;

@Component
public class SteamFormatter {
    private static final String UNKNOWN = "Indisponible";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);

    public void print(Result result, PrintWriter out, PrintWriter err) {
        out.println();
        heading(out, "STEAM LOOKUP", "Votre compte Steam en un coup d’œil");
        out.println("  SteamID64 : " + result.steamId());
        var profile = result.profile();
        section(out, "Profil");
        status(profile, "Profil", out, err);
        if (profile.data() != null) {
            out.println("  Nom : " + safe(profile.data().name()));
            out.println("  URL : " + safe(profile.data().url()));
        }
        section(out, "Bibliothèque");
        var library = result.library();
        status(library, "Bibliothèque", out, err);
        if (library.data() != null) {
            var data = library.data();
            out.println("  Jeux possédés : " + data.games().size());
            out.println("  Jeux lancés au moins une fois : " + data.playedCount()
                    + (data.completePlaytimes() ? "" : " (minimum connu)"));
            out.println("  Temps total : " + (data.completePlaytimes() ? hours(data.totalMinutes()) : UNKNOWN));
            if (data.games().isEmpty()) out.println("  Aucun jeu possédé.");
            else {
                out.println("\n  Vos 10 jeux les plus joués");
                var top = data.games().stream().sorted(SteamLookupService.MOST_PLAYED).limit(10).toList();
                List<List<String>> rows = new ArrayList<>();
                for (int i = 0; i < top.size(); i++) {
                    var game = top.get(i);
                    rows.add(List.of(Integer.toString(i + 1), safe(game.name()), hours(game.totalMinutes()), date(game.lastPlayed())));
                }
                table(out, List.of("#", "Jeu", "Temps total", "Dernière partie"), rows);
            }
        }
        section(out, "Activité récente");
        out.println("  Les 14 derniers jours · jusqu’à 5 jeux");
        var recent = result.recent();
        status(recent, "Activité récente", out, err);
        if (recent.data() != null) {
            if (recent.data().isEmpty()) out.println("  Aucun jeu joué durant les 14 derniers jours.");
            else {
                var rows = recent.data().stream().map(game -> List.of(safe(game.name()),
                        hours(game.recentMinutes()), date(game.lastPlayed()))).toList();
                table(out, List.of("Jeu", "Temps sur 14 jours", "Dernière partie"), rows);
            }
        }
        out.println("\n  Dates en UTC · Temps de jeu en heures");
        out.println();
        out.flush();
        err.flush();
    }

    static void heading(PrintWriter out, String title, String subtitle) {
        boolean color = System.console() != null && System.getenv("NO_COLOR") == null
                && !"dumb".equals(System.getenv("TERM"));
        out.println("  " + (color ? "\u001b[1;36m" : "") + title + (color ? "\u001b[0m" : ""));
        out.println("  " + subtitle);
        out.println("  " + "─".repeat(58));
    }

    private static void section(PrintWriter out, String title) {
        out.println("\n  ── " + title + " " + "─".repeat(Math.max(1, 53 - title.length())));
    }

    private static void table(PrintWriter out, List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int col = 0; col < widths.length; col++) {
            widths[col] = headers.get(col).codePointCount(0, headers.get(col).length());
            for (var row : rows) widths[col] = Math.max(widths[col], Math.min(32,
                    row.get(col).codePointCount(0, row.get(col).length())));
        }
        border(out, widths, "┌", "┬", "┐");
        row(out, widths, headers);
        border(out, widths, "├", "┼", "┤");
        for (var values : rows) row(out, widths, values);
        border(out, widths, "└", "┴", "┘");
    }

    private static void border(PrintWriter out, int[] widths, String left, String middle, String right) {
        out.print("  " + left);
        for (int col = 0; col < widths.length; col++) {
            out.print("─".repeat(widths[col] + 2));
            out.print(col == widths.length - 1 ? right : middle);
        }
        out.println();
    }

    private static void row(PrintWriter out, int[] widths, List<String> values) {
        out.print("  │");
        for (int col = 0; col < widths.length; col++) {
            String text = values.get(col);
            int length = text.codePointCount(0, text.length());
            if (length > widths[col]) {
                text = text.substring(0, text.offsetByCodePoints(0, widths[col] - 1)) + "…";
                length = widths[col];
            }
            out.print(" " + text + " ".repeat(widths[col] - length + 1) + "│");
        }
        out.println();
    }

    private static void status(Section<?> section, String title, PrintWriter out, PrintWriter err) {
        if (section.status() == Status.ERROR) {
            out.println("  ! Échec de consultation — les autres sections restent disponibles.");
            err.println(title + " : " + section.message());
        } else if (section.message() != null) out.println("  " + section.message());
    }

    // Les champs Steam sont externes : empêcher les contrôles et les séquences de terminal.
    static String safe(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value.replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    }
    private static String hours(Long minutes) {
        return minutes == null ? UNKNOWN : String.format(Locale.FRANCE, "%.1f h", minutes / 60.0);
    }
    private static String date(Instant instant) { return instant == null ? UNKNOWN : DATE.format(instant); }
}
