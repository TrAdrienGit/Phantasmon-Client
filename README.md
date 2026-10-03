# Phantasmon Client

> Client-only Fabric mod for Minecraft 1.21.1 + Cobblemon 1.8.1. Players create, edit, send out, trade and battle fully custom **Ghost Pokémon** — visible only to other players who also run the mod.

## Overview

Phantasmon never touches the Minecraft server: no server mod, no plugin, no server-side entity. The mod talks only to the [Phantasmon Backend](https://github.com/TrAdrienGit/Phantasmon-Backend), the single source of truth, over REST (data) and one WebSocket (presence, Ghosts, live trades, live battles). A player without the mod sees nothing at all.

Full documentation (player guide, commands, architecture, build and QA guides, specifications, project status) lives in [`Documentation/`](./Documentation/README.md), in French.

## Features

| Feature | How |
|---|---|
| **Login** | Automatic on world/server join when the backend is up (Mojang session proof → JWT, kept in memory and auto-refreshed). Manual fallback: `/phantasmon login`. Version handshake first; outdated clients get a download link. |
| **PC** | `/phantasmon pc` or **P**. Team rail, full Pokémon card (3D model, types, item, nature ±, ability, Tera, Hidden Power, moves with types, IV/EV), 16 boxes × 30 slots. Drag & drop between any slots (move or swap), mouse wheel to change box — also mid-drag. Showdown import from the clipboard. Delete with confirmation. |
| **Editor** | **ÉDITER** in the PC. Live preview card + form: nickname, gender (when the species allows it), level, shiny, ability (species' own), held item (battle items, searchable), nature, Tera type, IVs/EVs (live EV total, Hidden Power), 4 moves (searchable, no duplicates). Showdown paste pre-fills the form. |
| **Ghost Pokémon** | **O** or `/phantasmon sendout` toggles your team lead out/in, with Cobblemon's Poké Ball animations. Rendered client-side with Cobblemon's own models (forms, gender, shiny), follows you smoothly, roams around when you stand still, despawns on recall, death, dimension change or disconnect. |
| **Live trade** | **G** while looking at a player, Cobblemon's interaction wheel (**R** → *Ghost Trade*), or `/phantasmon trade invite <name>`. The other player clicks **[Accept]** in chat; both get the trade screen, pick an offer, flag *ready* — the backend swaps both Pokémon atomically (each takes the other's team slot). |
| **Ghost battle** | **B** while looking at a player, the wheel (**R** → *Ghost Battle*), or `/phantasmon battle invite <name>`. The backend picks a host client (alternating), which runs Cobblemon's own battle engine; both players get Cobblemon's native battle UI, send-out/recall and move animations. Optional 90 s turn timer (`/phantasmon battle timer`). |
| **Async trade (commands)** | `/phantasmon trade propose\|accept\|cancel\|view\|list` — offer by UUID, accepted later. |
| **Commands** | `/phantasmon pokemon import\|list\|pc\|delete\|clone\|edit\|team` for everything the screens do. Full list: [`commands-and-keybinds.md`](./Documentation/reference/commands-and-keybinds.md). |

All player-facing text is translated (French and English); backend errors arrive as `ERROR_*` codes and are translated locally. Keybinds are rebindable under *Options → Controls → Phantasmon*.

## Tech stack

| | |
|---|---|
| Minecraft | 1.21.1 (official Mojang mappings) |
| Loader | Fabric Loader ≥ 0.18.1, Fabric API 0.116.17+1.21.1 |
| Cobblemon | 1.8.1 (Fabric) |
| Language | Java 21 |
| Build | Gradle + Fabric Loom — **needs a JDK 25 to run Gradle** (compiled bytecode still targets Java 21) |
| Tests | JUnit 5 |

## Requirements

- **JDK 25** to build (Loom requirement). JDK 21 is enough to *play*.
- A Minecraft 1.21.1 client with Fabric Loader, Fabric API and Cobblemon 1.8.1.
- A reachable Phantasmon Backend. Its address is currently a constant in `src/client/java/com/mystaria/phantasmon/client/network/BackendConfig.java`.
- A premium (Microsoft) Minecraft account — offline accounts are not supported.

## Build & run

```bash
./gradlew build        # jar in build/libs/phantasmon-client-<version>.jar
./gradlew runClient    # development Minecraft instance with the mod loaded
./gradlew test
```

On a machine whose default Java is 21, point Gradle at a JDK 25 for that command only, e.g.:

```bash
JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot" ./gradlew build
```

A local, untracked `scripts/deploy-to-prod-server.sh` builds once and copies the jar to the test instances. Guides: [build](./Documentation/guides/building.md), [install](./Documentation/guides/installing.md), [testing & QA](./Documentation/guides/testing-and-qa.md); the history of every development pass is in [`development-journal.md`](./Documentation/project/development-journal.md).

## Project structure

```text
src/client/java/com/mystaria/phantasmon/client/
├── auth/        # Mojang join proof, JWT session, auto-refresh
├── battle/      # Ghost battles: host-side Cobblemon engine, relay, visuals, move animations
├── command/     # /phantasmon … (Brigadier, client-side)
├── ghost/       # presence WebSocket session, client-only Ghost entities
├── gui/         # PC, editor and trade screens on a shared canvas "design system"
├── mixin/       # 7 Mixins (battle choices, pure-client Showdown, move animations, interaction wheel)
├── network/     # REST + WebSocket clients, error-code translation
├── pokemon/     # DTOs, Showdown parser/mapper, natures, Hidden Power, gender
├── trade/       # live trade state/controller, async trade commands
├── version/     # client/backend version compatibility
└── wheel/       # Ghost Trade / Ghost Battle entries in Cobblemon's interaction wheel
src/main/resources/assets/phantasmon/
├── lang/        # fr_fr.json, en_us.json
└── textures/gui/  # PC sprites + trade/ (generated by scripts/generate_trade_textures.py)
```

The three screens extend `gui/PhantasmonCanvasScreen`: a 1600×900 px layout drawn at 80 % of the window, with pixel-snapped lines and text, shared panels, slots, Pokémon card, buttons and modals.

## Tests

`./gradlew test` covers the logic that doesn't need a running game: Showdown parsing and identifier mapping, natures, Hidden Power, gender resolution, version compatibility, live-trade state, Gson UUID handling. Rendering and world interaction are checked by manual QA (see the building doc).

## License

GNU General Public License v3.0 — see [`LICENSE`](./LICENSE).
