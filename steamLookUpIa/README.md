# steamLookUpIA

Outil en ligne de commande développé par IA, qui consulte la Web API Steam et affiche le profil, la bibliothèque et l’activité récente d’un compte. Aucun compte Steam n’est modifié et aucune donnée n’est enregistrée localement.

## Prérequis et compilation

- JDK 25 ; aucune installation globale de Gradle n’est nécessaire.
- Accès Internet pour le premier téléchargement du wrapper et des dépendances.
- Une clé Steam Web API pour les consultations réelles.

Depuis ce dossier :

```sh
./gradlew clean build
```

Sous Windows, utiliser `gradlew.bat` à la place de `./gradlew`. Le JAR exécutable est `build/libs/steamLookUpIA-0.0.1-SNAPSHOT.jar` ; le fichier `-plain.jar` n’est pas le livrable à lancer.

## Configuration

Obtenir une clé sur [Steam Web API Key](https://steamcommunity.com/dev/apikey), puis copier le fichier d’exemple :

```sh
cp .env.example .env
```

Renseigner la clé dans `.env` :

```properties
STEAM_API_KEY=votre_cle_steam
```

Une variable d’environnement `STEAM_API_KEY` est également acceptée et prend priorité sur `.env`. Le fichier `.env` est lu dans le **répertoire courant au lancement**, pas relativement au JAR. Ne jamais le committer. L’aide et les tests n’ont pas besoin de clé.

## Utilisation

Sur macOS et Linux, la commande courte prépare le JAR au premier lancement ou après un changement du code, puis lance directement l’application sans les messages de tâche Gradle :

```sh
./steam little_Almie
./steam --help
./steam
```

Sans argument dans un vrai terminal, `./steam` demande le nom, le SteamID64 ou l’URL du compte. Une saisie vide ou une fin d’entrée annule la recherche. Sans terminal interactif, fournir explicitement un identifiant. Le lanceur utilise le dossier du projet pour lire `.env`, même s’il est appelé depuis un autre dossier. Il utilise Java depuis `JAVA_HOME` si défini, sinon depuis le `PATH`. Si ce Java est trop ancien, le lanceur sélectionne automatiquement un JDK 25 installé sur macOS ; à défaut, il affiche les instructions de configuration.

Autres exemples :

```sh
./steam 76561198000000000
./steam "https://steamcommunity.com/id/nom-personnalise/"
./steam "https://steamcommunity.com/profiles/76561198000000000/"
./gradlew bootRun --args="little_Almie"
java -jar build/libs/steamLookUpIA-0.0.1-SNAPSHOT.jar --help
```

Sous Windows, utiliser le JAR ou `gradlew.bat bootRun --args="little_Almie"`. Le script `steam` est un lanceur shell macOS/Linux.

Les identifiants ci-dessus sont des exemples de syntaxe, sans garantie de compte accessible. Un seul identifiant est accepté par exécution. Les URL HTTP/HTTPS de `steamcommunity.com` et `www.steamcommunity.com` sont reconnues ; les liens ne sont jamais suivis directement. Le nom personnalisé est le segment `/id/...` de l’URL Steam, pas nécessairement le nom affiché du joueur.

### Résultat

Exemple fictif pour illustrer la présentation :

```text
  STEAM LOOKUP
  Votre compte Steam en un coup d’œil
  ──────────────────────────────────────────────────────────
  SteamID64 : 76561198000000000

  ── Profil ───────────────────────────────────────────────
  Nom : Alice
  URL : https://steamcommunity.com/id/alice/

  ── Bibliothèque ─────────────────────────────────────────
  Jeux possédés : 2
  Jeux lancés au moins une fois : 1
  Temps total : 1,5 h

  Vos 10 jeux les plus joués
  ┌───┬──────────┬─────────────┬─────────────────┐
  │ # │ Jeu      │ Temps total │ Dernière partie │
  ├───┼──────────┼─────────────┼─────────────────┤
  │ 1 │ Portal   │ 1,5 h       │ 2026-11-16      │
  │ 2 │ Portal 2 │ 0,0 h       │ Indisponible    │
  └───┴──────────┴─────────────┴─────────────────┘

  ── Activité récente ─────────────────────────────────────
  Les 14 derniers jours · jusqu’à 5 jeux
  ┌────────┬────────────────────┬─────────────────┐
  │ Jeu    │ Temps sur 14 jours │ Dernière partie │
  ├────────┼────────────────────┼─────────────────┤
  │ Portal │ 0,5 h              │ 2026-11-16      │
  └────────┴────────────────────┴─────────────────┘

  Dates en UTC · Temps de jeu en heures
```

Les tableaux s’adaptent aux noms des jeux ; les noms longs sont raccourcis avec `…`. Le titre est coloré uniquement dans un terminal interactif ; `NO_COLOR=1 ./steam little_Almie` désactive la couleur. Les sorties redirigées restent sans séquences de couleur.

La bibliothèque représente les jeux que Steam retourne comme possédés, sans demander l’inclusion supplémentaire des jeux gratuits joués. Le top contient au maximum dix jeux, triés par temps total décroissant, puis nom et identifiant en cas d’égalité. Les jeux jamais lancés restent inclus dans la bibliothèque.

L’activité contient au maximum cinq jeux de l’API récente (quatorze derniers jours), triés par dernière partie connue. Lorsque la réponse récente ne fournit pas cette date, la bibliothèque peut la compléter. Les dates connues hors période sont exclues ; les jeux sans date sont conservés à la fin, avec un ordre stable par identifiant. Sans date fournie par Steam, leur ordre chronologique exact ne peut pas être déterminé.

Les heures sont arrondies à une décimale et les dates affichées en UTC. Les informations manquantes portent la mention « Indisponible ». Si un temps de jeu manque, le total est indisponible et le nombre de jeux lancés indique le minimum connu.

### Confidentialité, erreurs et codes de sortie

| Code | Signification |
| --- | --- |
| `0` | Consultation sans erreur technique ou aide affichée ; inclut les données privées ou masquées. |
| `1` | Échec Steam : compte introuvable, clé refusée, réseau, réponse invalide ou échec partiel. |
| `2` | Arguments invalides ou clé absente. |

Les résultats sont écrits sur la sortie standard et les erreurs sur la sortie d’erreur. En cas d’échec partiel, les sections disponibles restent affichées. Les réponses absentes ou masquées ne sont pas qualifiées de privées sans indication explicite de Steam. La confidentialité du profil ne permet pas de déduire celle de la bibliothèque ou de l’activité. Un compteur explicitement nul ou une liste vide signale une section vide ; une réponse sans ces informations signale des données absentes ou masquées.

Les délais sont de cinq secondes pour la connexion et quinze secondes pour la réponse. Il n’y a ni relance automatique ni persistance. Les messages d’erreur ne propagent ni URI contenant la clé, ni corps HTTP, ni trace des exceptions réseau. Les journaux Spring sont désactivés pour préserver une sortie CLI lisible.

Pour vérifier le code de sortie sous macOS/Linux, exécuter `echo $?` juste après la commande Java. `bootRun` est pratique en développement, mais Gradle retourne son propre code d’échec ; utiliser `./steam` ou le JAR pour distinguer les codes `1` et `2` (après une compilation réussie).

## Architecture

| Couche | Responsabilité |
| --- | --- |
| `cli` | Arguments, aide, affichage français et codes de sortie. |
| `service` | Validation/résolution des identifiants, orchestration et classements. |
| `steam` | Configuration, HTTP avec `RestClient`, lecture JSON Jackson 3, erreurs maîtrisées. |
| `model` | Records métier, résultats et statuts de chaque section. |

Après résolution de l’identifiant, les trois consultations utilisent des threads virtuels Java. Chaque échec Steam est capturé séparément. Le contexte Spring se ferme après la commande ; aucun serveur web ne reste actif. Tous les packages Java utilisent la casse `steamlookupia`, et le point d’entrée Gradle est explicitement fixé pour éviter les ambiguïtés de casse sous macOS.

Les quatre opérations sont `ResolveVanityURL/v1`, `GetPlayerSummaries/v2`, `GetOwnedGames/v1` et `GetRecentlyPlayedGames/v1` sur `https://api.steampowered.com`.

Références : [Steam Web API](https://partner.steamgames.com/doc/webapi_overview), [IPlayerService](https://partner.steamgames.com/doc/webapi/IPlayerService), [ISteamUser](https://partner.steamgames.com/doc/webapi/ISteamUser).

## Tests

```sh
./gradlew test
```

Le rapport HTML est dans `build/reports/tests/test/index.html`. Les tests utilisent JUnit, Mockito et un serveur HTTP simulé Spring ; aucune clé, connexion Steam ou base de données n’est nécessaire. Ils couvrent les identifiants, les arguments, la configuration, les réponses JSON, la confidentialité, les résultats partiels, les statistiques, les tris, les limites et la protection des secrets dans les erreurs.

Une vérification réelle se fait séparément, après configuration de la clé, avec un compte dont les données de jeu sont accessibles. Les tests simulés ne garantissent pas la disponibilité de Steam ni la visibilité d’un compte donné.

## Réalisation par IA

La conception, l’implémentation Java, les tests et cette documentation ont été produits par l’IA à partir de la demande utilisateur. La version manuelle a servi à comprendre l’intention initiale et la version SpecKit à fixer le périmètre fonctionnel. L’implémentation est indépendante, sans intégration de SpecKit dans ce dépôt.

« IA » décrit ici la méthode de développement : l’application utilise uniquement l’API Steam et n’a besoin d’aucun abonnement ni d’aucune clé de fournisseur d’IA.
