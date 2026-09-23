---
name: phantasmon-client
description: >-
  Implements the Phantasmon Fabric client-only mod (Minecraft 1.21.1, Cobblemon 1.8.1).
  Use when editing Phantasmon-Client, mixins, fabric.mod.json, ghost rendering, REST/WS
  client, PC UI, i18n, or Minecraft client code for ghost Pokémon.
---

# Phantasmon Client

Repo: `Phantasmon-Client`. Fabric **client-only** (`fabric.mod.json` → `"environment": "client"`).

Also load `phantasmon-workspace` for CAD hierarchy and write boundaries.

## Hard rules

- Never generate a Minecraft **server** entrypoint, server mixin that changes world state, or code that registers a server-side Cobblemon entity for ghosts.
- Ghost Pokémon are **local client entities** driven by backend WebSocket events. Players without the mod see nothing.
- Do not call Cobblemon server battle as the authority for Ghost battles (Phase 9 uses a **host client** + backend guardrails).
- Never trust the client as source of truth: CRUD, trades, ownership always go to the backend.
- No hardcoded UI strings. Add keys to both `lang/fr_fr.json` and `lang/en_us.json`.
- Translate backend `error_code` values locally.
- Call `GET /version` **before** Mojang auth.
- JWT in memory + refresh; premium/online accounts only (offline/cracked out of scope).
- Do not implement positional sanity checks, quotas, or a custom auto-updater.

## Current skeleton (Phase 0)

- Mod id: `phantasmon` (display name **Phantasmon**)
- Packages: `com.mystaria.phantasmon` / `com.mystaria.phantasmon.client`
- `"environment": "client"` in `fabric.mod.json`
- **Cobblemon is not yet a Gradle dependency** — add 1.8.1 as `modImplementation` when starting real Cobblemon work
- Mixin configs exist but are empty; do not reintroduce the Fabric `ExampleMixin` on `MinecraftServer`

Stack from `gradle.properties`: Minecraft `1.21.1`, loader `0.19.5`, Java 21, Fabric API `0.116.17+1.21.1`, Loom, official Mojang mappings.

## Client phases (do not skip)

5. Auth UI/command, JWT, `GET /version`, i18n
6. Pokémon editor, PC boxes, team, Showdown import **mapping table** (built here, not earlier)
7. Ghost entity: reuse Cobblemon models/animations; spawn/move/despawn from WS; lifecycle on disconnect/death/dimension change
8. Trade UI on Phase 3 backend
9. Battle with host client

Rendering and world interaction: manual QA, not forced unit tests. Identifier resolution, validation helpers, state machines: JUnit.

## REST / WS (consume, do not invent)

REST: OpenAPI in the backend repo `Documentation/phantasmon-backend-openapi.yaml`.

WebSocket: `wss://…/ws?token={jwt}`

C2S: `JoinServerGroup`, `LeaveServerGroup`, `PositionUpdate`, `SendOutGhost`, `RecallGhost`, `BattleAction`, `Heartbeat`

S2C: `GhostEntitySpawn`, `GhostEntityMove`, `GhostEntityDespawn`, `BattleState`, `BattleEnded`, `TradeProposed`, `TradeAccepted`, `TradeCancelled`, `Error`, `HeartbeatAck`

`server_fingerprint` = hash of joined Minecraft server `ip:port`. Grouping is `fingerprint + dimension`.

## Cobblemon usage

Resolve species/form/move/ability/item via **local Cobblemon data**. If `cobblemon_data_version` mismatches and an id is missing: show a translated fallback, **do not** rewrite the Pokémon silently.

Inspect the `cobblemon` workspace repo for public APIs. Prefer documented events/APIs over internals; keep notes of what is internal.

Showdown ↔ Cobblemon id mapping is a Phase 6 deliverable. Until then, do not invent a full mapping table.

## i18n and Ghost label

Display `[Ghost]` (or translated equivalent via lang files) next to ghost Pokémon names (CAD Partie 1 §5).
