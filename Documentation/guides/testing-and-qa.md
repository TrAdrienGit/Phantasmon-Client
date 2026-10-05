# Tests et recette manuelle

## 1. Tests automatisés

```bash
./gradlew test
```

Seule la **logique pure** (sans Minecraft) est testée automatiquement ; le rendu et les interactions avec le monde
relèvent de la recette manuelle ci-dessous (choix assumé du CAD Partie 4).

| Classe de test | Couvre |
|---|---|
| `pokemon/showdown/ShowdownParserTest` | Analyse du texte Showdown, plusieurs Pokémon, valeurs par défaut |
| `pokemon/showdown/ShowdownImportMapperTest` | Conversion vers la requête de création (IV 31 / EV 0 par défaut) |
| `pokemon/showdown/CobblemonIdentifiersTest` | Normalisation des identifiants, exceptions à tiret |
| `pokemon/HiddenPowerCalculatorTest` | Type de Puissance Cachée |
| `pokemon/NatureModifiersTest` | Bonus/malus des natures |
| `pokemon/PokemonGenderTest` | Sexe stocké ou imposé par l'espèce |
| `version/VersionCompatibilityTest` | Comparaison de versions |
| `trade/LiveTradeStateTest` | Miroir de l'état d'échange en direct |
| `network/GsonUuidSanityTest` | Un `UUID` est sérialisé en chaîne par Gson (garde-fou permanent) |

Le jeu de sources `client` est branché sur les tests dans `build.gradle`. Toute nouvelle logique sans dépendance à
Minecraft doit être extraite dans une classe testable et couverte.

## 2. Préparer une recette

- Backend lancé (voir `Phantasmon-Backend/Documentation/guides/running.md`) et joignable à l'adresse de
  `backend_url` (`config/phantasmon.json` de l'instance).
- Mod compilé et installé (voir [`deployment.md`](deployment.md) pour les instances de test).
- Un compte Microsoft ; **deux comptes** pour les échanges, la visibilité mutuelle et les combats.
- Garder le log du jeu (`logs/latest.log`) et la console du backend ouverts.

## 3. Recette par fonctionnalité

### Connexion

- [ ] Entrée dans un monde, backend actif : connexion automatique, aucune action requise.
- [ ] Backend arrêté : aucun message parasite ; `/phantasmon login` affiche une erreur réseau traduite.
- [ ] `phantasmon.version.min-supported` du backend au-dessus de la version du mod : refus avec lien de mise à jour.
- [ ] `phantasmon.jwt.access-ttl=PT3M` : après 3 minutes, le log du backend montre un `POST /auth/refresh -> 200`.
- [ ] Jeu en anglais puis en français : aucun texte sous forme de clé brute.

### PC et éditeur

- [ ] **P** ouvre le PC ; équipe, fiche et grille affichées ; modèles 3D centrés dans les cases.
- [ ] Glisser PC → équipe vide, PC → équipe occupée (échange), équipe ↔ équipe, PC ↔ PC, vers une autre boîte.
- [ ] IMPORTER avec plusieurs Pokémon Showdown ; texte invalide → message d'erreur.
- [ ] SUPPRIMER → confirmation → Pokémon disparu.
- [ ] Éditeur : chaque champ, recherche d'objet et d'attaque, pas de doublon d'attaque, total EV > 510 en rouge,
      IMPORTER dans l'éditeur, ENREGISTRER, Échap avec modifications (confirmation).
- [ ] Sexe : espèce mixte (bascule), espèce à sexe fixe ou asexuée (grisé), import avec `(M)` / `(F)`.
- [ ] Formes (Arceus Fée, Motisma Lavage, Ogerpon Fontaine…) et chromatiques correctement rendus.
- [ ] Échelles d'interface 1 à 4 et fenêtre 1280×720 : écrans entiers, centrés, cliquables.

### Ghost

- [ ] **H** : animation de sortie, le Ghost suit ; **H** à nouveau : rappel animé.
- [ ] Emplacement 1 vide : message d'erreur.
- [ ] Tourner la caméra sur place : le Ghost ne bouge pas. Immobile 5 s : il se promène.
- [ ] Gros Pokémon (Arceus, Rayquaza) : ne pousse pas le joueur, en marchant, en sprintant, en demi-tour.
- [ ] Espèces volantes : vol stationnaire.
- [ ] Mort, changement de dimension, déconnexion : le Ghost disparaît (aussi chez l'autre joueur).
- [ ] Second joueur arrivé après la sortie : il voit le Ghost sans que le propriétaire bouge.

### Échange en direct (deux comptes)

- [ ] Invitation par la roue **R** (« Échange Ghost ») ; [Accepter] ouvre l'écran chez les deux.
- [ ] Changer d'offre met à jour la fiche de l'autre et remet « prêt » à faux des deux côtés.
- [ ] Les deux prêts : animation, « échange terminé », fermeture automatique ; le Pokémon reçu est à
      l'emplacement d'équipe du Pokémon donné.
- [ ] QUITTER (ou Échap) → confirmation → l'autre écran se ferme avec un message.
- [ ] Invitation laissée sans réponse 60 s : expirée.

### Combat (deux comptes)

- [ ] Invitation par la roue **R** (« Combat Ghost ») ; [Accepter] lance l'interface de combat Cobblemon
      chez les deux.
- [ ] Les deux sens d'hébergement : hôte sur serveur intégré (LAN) **et** hôte client pur (l'hôte alterne d'un
      combat à l'autre).
- [ ] Attaques, changements, K.O., objets, boosts, statuts ; animations de sortie, de rappel et d'attaque.
- [ ] Chrono activé : compte à rebours, action automatique à l'expiration.
- [ ] Abandon : victoire de l'autre. Déconnexion : combat annulé.

### Roue d'interaction

- [ ] **R** sur un joueur : entrées Cobblemon intactes + « Échange Ghost » et « Combat Ghost ».

## 4. Signaler un problème

Joindre : la commande ou l'action, le message affiché, les lignes pertinentes de `logs/latest.log`
(recherche : `Phantasmon`, `Ghost`, `Cannot`, `WARN`) et de la console du backend (requêtes `->` avec leur statut,
`WebSocket connected`, `joined group`, `sent out Ghost`).
