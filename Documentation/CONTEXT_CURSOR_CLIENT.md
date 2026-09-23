# Contexte projet — Ghost Pokémon (à destination des IA de Cursor)

Ce document résume le Cahier des charges (CAD, Parties 1 à 3) pour donner aux assistants IA du contexte de développement cohérent sur ce repo. À lire avant toute génération de code.

## 1. Résumé du projet

Ghost Pokémon est un addon Fabric pour Minecraft 1.21.1 + Cobblemon, qui permet aux joueurs de créer, éditer, échanger et combattre avec des Pokémon "fantômes" totalement custom, gérés par un backend externe indépendant du serveur Minecraft.

**Règle d'or à ne jamais violer dans le code généré : le serveur Minecraft/Cobblemon ne contient et ne contiendra jamais aucune ligne de code Ghost.** Toute la logique passe par le Ghost Client (mod client-only) qui parle directement au Ghost Backend.

## 2. Composants du repo

```text
ghost-pokemon/
├── ghost-client/     # Mod Fabric, CLIENT-ONLY (environment=client dans fabric.mod.json)
└── ghost-backend/    # Spring Boot, indépendant, aucune dépendance Minecraft/Fabric
```

Ce sont deux projets séparés (build Gradle distinct pour le client, Maven/Gradle Spring Boot pour le backend), pas un monorepo avec module commun côté Minecraft serveur — il n'y a pas de module serveur Minecraft.

## 3. Stack technique

| | |
|---|---|
| Minecraft | 1.21.1 |
| Cobblemon | 1.8.1 (mainteneur à suivre pour les montées de version) |
| Loader | Fabric uniquement (pas NeoForge/Forge) |
| Langage mod | Java |
| Build mod | Gradle (Fabric Loom) |
| Backend | Java 21, Spring Boot 3.x |
| Base de données | PostgreSQL (colonnes + JSONB hybride) |
| API | REST + WebSocket |
| Auth | Vérification de session Mojang (`joinServer`/`hasJoined`) → JWT |
| CI locale | TDD strict, suite de tests rejouée à chaque commit |

## 4. Principes d'architecture non négociables

1. **Pas de mod serveur Minecraft.** Aucune entité Minecraft réelle pour un Ghost Pokémon — tout rendu de Ghost Pokémon est une entité **purement client**, gérée par le renderer local, jamais synchronisée via le protocole vanilla d'entités.
2. **Peer-to-peer via backend.** Les clients ne se parlent jamais directement ; tout transite par le Ghost Backend (REST pour le CRUD, WebSocket pour le temps réel : spawn/despawn/position/combat/trade).
3. **Backend = source de vérité absolue.** Le client ne doit jamais être considéré comme fiable pour une opération sensible (ownership, modification, suppression, échange, combat). Toute vérification d'ownership se fait côté backend.
4. **Pas de duplication des données Cobblemon.** Le backend ne stocke que des identifiants (espèce, forme, capacité, attaque, objet) sous forme de chaînes stables ; jamais les stats de base, animations ou effets, qui restent résolus localement via les données Cobblemon présentes chez le joueur.
5. **Aucune interaction avec le gameplay Cobblemon réel.** Pas d'XP, pas d'évolution automatique, pas de breeding, pas de combat contre un Pokémon sauvage réel. Seule passerelle vers le "réel" : une commande de spawn Cobblemon utilisée manuellement par un OP — hors périmètre du code Ghost.
6. **Modèle hybride SQL/JSONB.** Champs filtrables/indexables en colonnes (`species`, `owner_uuid`, `level`...), reste en JSONB (`ivs`, `evs`, `moves`, `held_item`...).
7. **Légalité des Pokémon imposée côté backend** (jamais côté client seul) : IVs ∈ [0,31]/stat, EVs ∈ [0,252]/stat avec somme ≤ 510, moveset et ability cohérents avec l'espèce/forme.
8. **Idempotence.** Toute opération de création/modification sensible porte un `request_uuid` ; le backend déduplique via la table `idempotency_keys`.
9. **i18n obligatoire dès la V1** (FR + EN). Aucune chaîne en dur : `lang/fr_fr.json` / `lang/en_us.json` côté client, codes d'erreur (`ERROR_...`) côté backend, jamais de texte brut serveur non traduit.
10. **TDD strict.** Un test avant le code pour chaque feature. La suite complète est rejouée à chaque commit.

## 5. Modules fonctionnels attendus

- **Pokémon** : CRUD, clone, import Showdown, export, pagination PC (`?box=n`).
- **Équipe active** : lecture/écriture de la team du joueur.
- **Ghost Entity** : spawn/despawn/déplacement, rendu réutilisant modèles et animations Cobblemon, cycle de vie sur déconnexion/mort/téléportation/changement de dimension.
- **Trade** : proposition/acceptation/annulation, atomique (transaction unique), pas de timeout automatique.
- **Combat Ghost** : session de combat (`battle_uuid`), modèle "client hôte + garde-fous backend" (limitation connue et assumée, pas un bug), état de combat temporaire non persistant (le `GhostPokemon` reprend son état de base après combat).
- **Présence & groupement** : regroupement des joueurs par `server_fingerprint` (hash IP:port du serveur Minecraft rejoint) + `dimension`, avec TTL de nettoyage des sessions orphelines en cas de crash client.
- **Auth** : flux Mojang `joinServer`/`hasJoined` → JWT court + refresh.
- **Version handshake** : `GET /version`, comparaison `client_version` / `min_supported_version` avant tout flux d'auth.
- **Admin** : routes `/admin/*` protégées par rôle JWT, jamais accessibles depuis une action client normale.
- **Logs** : logging maximal (CRUD, connexions, combats, erreurs techniques, actions admin).

## 6. Ce qui n'est PAS dans le périmètre (ne pas générer de code pour ça sans demande explicite)

- Aucun mod côté serveur Minecraft.
- Aucune intégration avec le moteur de combat serveur Cobblemon.
- Aucun système de quota/cooldown/coût de création de Pokémon (accès libre confirmé).
- Aucune validation de plausibilité de position réseau (anti-triche positionnel explicitement écarté).
- Aucune scalabilité multi-instance backend (Redis/pub-sub) — instance unique assumée pour l'instant.

## 7. Convention d'API — extraits utiles pour générer du code cohérent

```http
POST   /auth/session
GET    /version
GET    /players/{uuid}/pokemon
GET    /players/{uuid}/pc?box={n}
POST   /pokemon                 # header/idempotency: request_uuid
PATCH  /pokemon/{uuid}
DELETE /pokemon/{uuid}
POST   /pokemon/{uuid}/clone
POST   /pokemon/import-showdown
POST   /trades
POST   /trades/{uuid}/accept
POST   /trades/{uuid}/cancel
POST   /battles
POST   /battles/{uuid}/result
GET    /admin/pokemon/{uuid}
GET    /health
```

WebSocket : `wss://backend/ws?token={jwt}` — événements C2S (`SendOutGhost`, `RecallGhost`, `PositionUpdate`, `BattleAction`, `Heartbeat`) et S2C (`GhostEntitySpawn`, `GhostEntityMove`, `GhostEntityDespawn`, `BattleState`, `TradeProposed`, `Error`...).

Erreurs backend toujours sous forme de code, jamais de texte brut :

```json
{ "error_code": "ERROR_LEGALITY_EV_TOTAL_EXCEEDED", "details": { "total": 528, "max": 510 } }
```

## 8. Où trouver le détail complet

Le CAD complet (Parties 1 à 3) fait foi en cas de doute et doit être consulté avant toute décision d'architecture non couverte ici :

- Partie 1 — Cahier des charges fonctionnel
- Partie 2 — Architecture technique
- Partie 3 — Compléments (légalité, trades, handshake de version, TTL de présence, backup, i18n)
