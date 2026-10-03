# Documentation — Phantasmon Client

Mod Fabric **client uniquement** pour Minecraft 1.21.1 + Cobblemon 1.8.1 : création, édition, sortie, échange et
combat de **Ghost Pokémon**, en dialogue avec le
[Phantasmon Backend](https://github.com/TrAdrienGit/Phantasmon-Backend).

**Dernière révision complète : 2026-10-03.**

## Par où commencer

| Je veux… | Lire |
|---|---|
| Comprendre le projet en 5 minutes | [`architecture/system-overview.md`](architecture/system-overview.md) |
| Jouer avec le mod | [`guides/installing.md`](guides/installing.md), puis [`guides/user-guide.md`](guides/user-guide.md) |
| Compiler le mod | [`guides/building.md`](guides/building.md) |
| Connaître toutes les commandes et touches | [`reference/commands-and-keybinds.md`](reference/commands-and-keybinds.md) |
| Comprendre le code | [`architecture/client-architecture.md`](architecture/client-architecture.md) |
| Savoir où en est le projet | [`project/status.md`](project/status.md), [`project/known-issues.md`](project/known-issues.md) |
| Coder sur ce dépôt (humain ou agent IA) | [`agents/client-agent-context.md`](agents/client-agent-context.md) |

## Organisation

```text
Documentation/
├── README.md                        ce fichier
├── architecture/
│   ├── system-overview.md           vue d'ensemble du système (miroir)
│   ├── client-architecture.md       paquets, cycle de vie, fils, réseau, i18n, pièges
│   ├── ghost-entities.md            Ghost dans le monde : session, création, mouvement, disparition
│   ├── gui-design-system.md         canevas commun, écrans PC / éditeur / échange, rendu des modèles
│   ├── live-trade.md                échange en direct et asynchrone
│   ├── battle-engine.md             combat : moteur Cobblemon sur le client hôte, relais, animations
│   ├── mixins.md                    inventaire des Mixins et procédure de mise à jour de Cobblemon
│   ├── showdown-import.md           format Showdown et identifiants Cobblemon
│   └── decisions.md                 journal des décisions et écarts au CAD (miroir)
├── reference/
│   ├── commands-and-keybinds.md     toutes les commandes et touches
│   ├── translations.md              règles i18n, espaces de noms des clés
│   └── configuration.md             constantes, fichiers créés, build
├── guides/
│   ├── building.md                  JDK 25, build, runClient, textures, CI
│   ├── installing.md                installation côté joueur
│   ├── user-guide.md                guide du joueur
│   ├── testing-and-qa.md            tests unitaires et recette manuelle
│   ├── deployment.md                déploiement sur les instances de test, test à deux comptes
│   └── troubleshooting.md           dépannage
├── design/                          maquettes : écran d'échange (référence active), prototype PC (historique)
├── specifications/                  cahier des charges, Parties 1 à 4, annotées (miroir)
├── project/
│   ├── status.md                    avancement, non implémenté (miroir)
│   ├── known-issues.md              bugs connus, TODO, dette technique, limites (miroir)
│   └── development-journal.md       historique détaillé des passes de développement (§4.1 à §4.45)
├── agents/
│   └── client-agent-context.md      règles pour les agents IA qui codent ici
└── research/
    └── cobblemon-custom-pokemon.md  note sur le format des données Cobblemon (miroir)
```

**Miroir** : le document existe à l'identique dans le dépôt Backend. Une modification doit être reportée dans
les deux dépôts : `architecture/system-overview.md`, `architecture/decisions.md`, `project/status.md`, `project/known-issues.md`,
`specifications/*`, `research/cobblemon-custom-pokemon.md`.

## Documentation du backend

Les contrats que le client consomme sont documentés dans le dépôt Backend (`Documentation/reference/`) :
API REST, protocole WebSocket, catalogue des codes d'erreur, schéma de la base. Le schéma de la base n'est plus
dupliqué ici.

## Conventions

- **Langue** : documentation en français ; noms de fichiers en anglais, en minuscules avec tirets (sauf la
  maquette livrée `design/trade-screen/SPEC_ECRAN_ECHANGE.md`, nom d'origine conservé).
- **Format** : Markdown (GitHub Flavored), diagrammes Mermaid.
- **Source de vérité** : la documentation décrit le code **tel qu'il est** ; en cas de doute, le code et les tests
  font foi, puis [`architecture/decisions.md`](architecture/decisions.md), puis les
  [`specifications/`](specifications/README.md).
- **Dates** absolues (`AAAA-MM-JJ`).
- **Secrets** : jamais de mot de passe, jeton ou contenu de `.env`.

## Maintenance

Voir le tableau « Documentation à tenir à jour » de
[`agents/client-agent-context.md`](agents/client-agent-context.md#5-documentation-à-tenir-à-jour-dans-le-même-changement).
