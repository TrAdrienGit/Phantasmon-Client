# Phantasmon Client

> Client-only Fabric mod for Minecraft 1.21.1 + Cobblemon that lets players create, edit, trade, and battle with fully custom "ghost" Pokémon — visible only to players who have the addon installed.

## Overview

Phantasmon Client does not modify any Minecraft server. It talks directly to the [Phantasmon Backend](https://github.com/your-account/phantasmon-backend), an independent source of truth, over REST and WebSocket. No server-side installation is required — only players running the mod can create, see, and interact with each other's ghost Pokémon.

See the [full design document](https://github.com/your-account/phantasmon-docs) for the complete architecture, technical decisions, and development plan.

## Tech stack

| | |
|---|---|
| Minecraft | 1.21.1 |
| Cobblemon | 1.8.1 |
| Loader | Fabric |
| Language | Java 21 |
| Build | Gradle (Fabric Loom) |

## Requirements

- JDK 21
- A Minecraft 1.21.1 client with [Fabric Loader](https://fabricmc.net) and [Cobblemon 1.8.1](https://modrinth.com/mod/cobblemon) installed
- A reachable [Phantasmon Backend](https://github.com/your-account/phantasmon-backend) instance (local or remote)

## Running in development

```bash
git clone https://github.com/your-account/phantasmon-client.git
cd phantasmon-client
./gradlew runClient
```

This launches a development Minecraft instance with the mod already loaded.

## Build

```bash
./gradlew build
```

The generated jar is placed in `build/libs/`.

## Project structure

```text
phantasmon-client/
├── src/main/java/           # Mod code
├── src/main/resources/
│   ├── fabric.mod.json      # Manifest (environment: client)
│   └── lang/                # Translations (fr_fr, en_us)
└── build.gradle
```

## Tests

```bash
./gradlew test
```

Note: some code (rendering, direct interaction with the Minecraft world) isn't meaningfully unit-testable and is instead covered by manual QA. Business logic decoupled from Minecraft (identifier resolution, validation, state handling) is covered with plain JUnit tests.

## Distribution

Published on [Modrinth](https://modrinth.com) and [CurseForge](https://www.curseforge.com).

## License

CC0 1.0 Universal — see [`LICENSE`](./LICENSE).
