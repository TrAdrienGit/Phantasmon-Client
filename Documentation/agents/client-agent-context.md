# Contexte pour les agents IA — Phantasmon Client

> À lire avant toute génération de code sur ce dépôt. Remplace l'ancien `CONTEXT_CURSOR_CLIENT.md`.
> Mis à jour le 2026-10-03.

## 1. Le projet

Phantasmon est un mod Fabric **client uniquement** pour Minecraft 1.21.1 + Cobblemon 1.8.1 : les joueurs créent,
éditent, font sortir, échangent et font combattre des **Ghost Pokémon**, gérés par un backend Spring Boot
indépendant (dépôt `Phantasmon-Backend`), seule source de vérité.

**Règle d'or : le serveur Minecraft ne contient et ne contiendra jamais aucune ligne de code Phantasmon.**

Lire d'abord : [`architecture/system-overview.md`](../architecture/system-overview.md), puis
[`architecture/client-architecture.md`](../architecture/client-architecture.md).

## 2. Pile

Java 21 (bytecode) · Gradle + Fabric Loom 1.18 (**JDK 25 requis pour exécuter Gradle**) · **mappings Mojang
officiels** (`Minecraft`, `Component`, `GuiGraphics`, `displayClientMessage`… pas les noms Yarn) · Fabric API ·
Cobblemon 1.8.1 (identifiant Modrinth `gBW3vLC7`) · Gson · JUnit 5.

## 3. Règles non négociables

1. **Aucun code serveur.** Pas d'entité, de bloc, de registre ni de réseau Fabric côté serveur. Les Ghost sont des
   entités locales ajoutées au `ClientLevel`.
2. **Tout passe par le backend** : REST pour les données, **un seul** WebSocket (détenu par `GhostSession`) pour le
   temps réel. Les clients ne se parlent jamais directement.
3. **Le client n'est jamais l'autorité** : ne jamais appliquer localement un changement (offre d'échange, prêt,
   propriété) avant l'écho du serveur. Ne jamais envoyer d'`owner_uuid`.
4. **Identifiants Cobblemon uniquement** vers le backend ; tout le reste (stats, modèles, noms traduits) est résolu
   localement avec Cobblemon.
5. **i18n** : aucune chaîne visible en dur, clés dans `fr_fr.json` **et** `en_us.json` ; codes d'erreur traduits
   par `BackendErrorMessages`, jamais affichés bruts.
6. **JWT en mémoire uniquement**, jamais sur disque. Comptes hors-ligne refusés.
7. **Gameplay fermé** : pas d'XP, d'évolution, d'élevage, de combat contre des Pokémon sauvages réels.
8. **Tests** : toute logique pure (sans Minecraft) est extraite et testée en JUnit ; le rendu et le monde relèvent
   de la recette manuelle ([`guides/testing-and-qa.md`](../guides/testing-and-qa.md)).
9. **Mixins** en dernier recours, minces, `defaultRequire = 1`, `remap = false` pour Cobblemon ; documenter chaque
   ajout dans [`architecture/mixins.md`](../architecture/mixins.md).
10. **Pas de commit ni de push** : Adrien gère git. Ne jamais afficher ni commiter un `.env`.

## 4. Méthode

- **Décompiler avant d'écrire** du code touchant une API Minecraft, authlib ou Cobblemon peu connue (`javap -c -p`
  sur le jar réel du cache Loom). Les tutoriels Yarn ou d'autres versions sont souvent faux pour 1.21.1.
- Ne jamais ouvrir un écran directement depuis une commande : drapeau consommé au tick suivant.
- Rendu GUI : hériter de `PhantasmonCanvasScreen` et respecter ses règles (flush avant blit, échelles de texte
  entières, z des modales) : [`architecture/gui-design-system.md`](../architecture/gui-design-system.md).
- Après chaque build qui change le jar, déployer sur les instances de test (`scripts/deploy-to-prod-server.sh`,
  script local non versionné).

## 5. Documentation à tenir à jour dans le même changement

| Quand | Mettre à jour |
|---|---|
| Commande ou touche ajoutée, modifiée | `reference/commands-and-keybinds.md`, `guides/user-guide.md` |
| Clé de traduction ou code d'erreur | `reference/translations.md` (+ catalogue d'erreurs du backend) |
| Mixin ajouté, modifié | `architecture/mixins.md` |
| Changement de build, dépendance, prérequis | `guides/building.md`, `reference/configuration.md` |
| Nouvelle fonctionnalité ou comportement visible | Document d'architecture concerné, `guides/testing-and-qa.md`, entrée dans `project/development-journal.md` |
| Écart au CAD, choix structurant | `architecture/decisions.md` (miroir dans le dépôt Backend) |
| Fonctionnalité terminée, limite découverte | `project/status.md` (miroir dans le dépôt Backend) |
| Bug découvert, TODO, dette technique | `project/known-issues.md` (miroir dans le dépôt Backend) |

## 6. Hors périmètre sans demande explicite

- Tout mod ou plugin serveur ; toute intégration au moteur de combat serveur d'un vrai serveur Cobblemon.
- Quotas, coûts ou délais de création.
- Contrôle de plausibilité des positions.
- Combats contre des Pokémon sauvages ou des joueurs sans le mod.
- Modifier les dépôts de référence en lecture seule (`cobblemon`, `fabric`, `pokemon-showdown`…).

## 7. Hiérarchie des sources en cas de contradiction

1. Le code et ses tests ; puis ce document et `architecture/decisions.md`.
2. `specifications/cad-4-plan-developpement.md`, puis `cad-3-complements.md`, puis `cad-2-architecture-technique.md`.
3. Contrats du backend (`Phantasmon-Backend/Documentation/reference/`).
4. `specifications/cad-1-specifications-fonctionnelles.md` pour l'intention produit uniquement.
