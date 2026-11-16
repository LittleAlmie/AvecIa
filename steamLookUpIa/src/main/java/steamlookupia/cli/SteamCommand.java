package steamlookupia.cli;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import steamlookupia.service.SteamIdentifierResolver;
import steamlookupia.service.SteamLookupService;
import steamlookupia.steam.SteamException;
import steamlookupia.steam.SteamProperties;

@Component
public class SteamCommand implements ApplicationRunner, ExitCodeGenerator {
    private final SteamLookupService service;
    private final SteamIdentifierResolver resolver;
    private final SteamProperties properties;
    private final SteamFormatter formatter;
    private int exitCode;

    public SteamCommand(SteamLookupService service, SteamIdentifierResolver resolver,
                        SteamProperties properties, SteamFormatter formatter) {
        this.service = service;
        this.resolver = resolver;
        this.properties = properties;
        this.formatter = formatter;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] input = args.getSourceArgs();
        if (input.length == 0 && System.console() != null) {
            System.console().printf("%n  STEAM LOOKUP · Profil et jeux Steam%n%n");
            String account = System.console().readLine("  Compte Steam (nom, ID ou URL) : ");
            if (account == null || account.isBlank()) {
                System.console().printf("%n  Recherche annulée.%n");
                return;
            }
            input = new String[] { account };
        }
        exitCode = execute(input, new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8));
    }

    public int execute(String[] args, PrintWriter out, PrintWriter err) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            usage(out);
            return 0;
        }
        if (args.length != 1) {
            err.println("Fournissez exactement un identifiant Steam.");
            usage(err);
            return 2;
        }
        try {
            String identifier = resolver.normalize(args[0]);
            if (!properties.configured()) {
                err.println("Clé Steam API absente. Obtenez une clé sur https://steamcommunity.com/dev/apikey");
                err.println("Configurez STEAM_API_KEY dans l’environnement ou dans le fichier .env du répertoire courant.");
                return 2;
            }
            out.println("\n  Recherche du compte Steam…");
            out.flush();
            var result = service.lookup(identifier);
            formatter.print(result, out, err);
            return result.hasErrors() ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            usage(err);
            return 2;
        } catch (SteamException exception) {
            err.println(exception.getMessage());
            return 1;
        } finally {
            out.flush();
            err.flush();
        }
    }

    private static void usage(PrintWriter out) {
        out.println();
        SteamFormatter.heading(out, "STEAM LOOKUP", "Profil · Bibliothèque · Activité récente");
        out.println("  Usage : ./steam <compte>");
        out.println();
        out.println("  Commandes");
        out.println("    ./steam                     Saisir un compte dans le terminal");
        out.println("    ./steam little_Almie        Rechercher un nom personnalisé");
        out.println("    ./steam 76561198000000000    Rechercher un SteamID64");
        out.println("    ./steam --help              Afficher cette aide");
        out.println();
        out.println("  Les URL steamcommunity.com/id/… et /profiles/… sont acceptées.");
        out.println("  Configuration : STEAM_API_KEY dans le fichier .env.");
        out.println("  Gradle : ./gradlew bootRun --args=\"little_Almie\"");
        out.println();
        out.flush();
    }

    @Override
    public int getExitCode() { return exitCode; }
}
