# Publishing on Modrinth and CurseForge: release checklist

Same process as Cobblemon Battle Showtime (`Cobblemon-Battle-Showtime/Documentation/publication/`). Page text for both
sites: [`description.md`](description.md), in Markdown (on CurseForge, pick the Markdown editor). Replace the
`<!-- GIF / Screenshot -->` comments with your images once they are in the gallery. Backend hosting guide linked from
the page: `Phantasmon-Backend/Documentation/guides/self-hosting.md`.

**Publish Cobblemon Battle Showtime 1.2.0 first**: Phantasmon requires it (`>=1.2.0`), and the version on Modrinth is
older.

## Decisions (settled 2026-11-18)

| Point | Decision |
|---|---|
| Sounds `pc_login.ogg`, `pc_logout.ogg`, `pressing_a.ogg` | Checked by Adrien: fine to publish. |
| Default backend address | Kept: the project's server (disclosed in "Network and privacy"). |
| Icon | Made by hand by Adrien. |
| Opening the menu | **H** opens the Phantasmon menu (hubs tab first, then rewards, music, settings). |
| Version | **0.1.0**, **Beta** channel, for Phantasmon and Phantasmon Events. |
| Order | Cobblemon Battle Showtime 1.2.0 first, then Phantasmon, then Phantasmon Events. |

## Fields to fill in

| Field | Value |
|---|---|
| Name | Phantasmon |
| Slug / URL | `phantasmon` (check it is free on both sites) |
| Summary (Modrinth, 256 characters max) | A second, fully custom Pokémon team on any Cobblemon server: build Ghost Pokémon, send them out, trade them and battle other players, under Showdown's rules. Hubs join players across servers. Needs a Phantasmon backend. |
| Summary (CurseForge) | Same text |
| Project type | Mod |
| Loader | Fabric |
| Minecraft version | 1.21.1 |
| Environment (Modrinth) | Client: required. Server: unsupported (not needed) |
| Categories (Modrinth) | Social, Utility, Game Mechanics; Cobblemon add-on if offered |
| Categories (CurseForge) | Addons, Multiplayer, Miscellaneous; Fabric |
| License | Apache-2.0 |
| Source | https://github.com/TrAdrienGit/Phantasmon-Client |
| Issues | https://github.com/TrAdrienGit/Phantasmon-Client/issues |
| Other link | Backend: https://github.com/TrAdrienGit/Phantasmon-Backend |
| Discord | Only if it is public (Modrinth: links must lead to public resources). |
| Icon | `src/assets/logo/Phantasmon_Icon.png` (1024 × 1024) |

## Modrinth rules: what to tick and check

Checked against Modrinth's Content Rules (version of 2026-08-13), as for Showtime.

| Rule | What it means for Phantasmon |
|---|---|
| 1.11 Data sent to a remote server must be clearly disclosed | **The main point.** The description's "Network and privacy" says what goes to the backend (account, Pokémon, a hash of the server address, dimension and position while playing) and to Mojang. Tick the **external system interaction** disclosure. Keep that section true if the network behaviour changes. |
| Usefulness / public resources | The mod needs a backend: the description says so at the top, explains who it is for, and links the self-hosting guide and the backend's source. That is what makes it usable by anyone. |
| 1.3 / 1.9 / 4 Trademarks, endorsement, copyright | No game assets once the sounds are settled; the "not affiliated" notice ends the description. |
| 2.1 / 2.2 Clear description, English | Done; what it adds, how to use it, dependencies, network. |
| 3 Cheats | None: Ghosts and their battles never touch the Minecraft server's game, and give no advantage there. |
| 5.1 / 5.9 Metadata and disclosures | Environment: client required, server unsupported. License Apache-2.0. |
| 5.6 Dependencies on each version | Table below. |
| **6.1 AI disclosure** | **Tick "Contains AI-generated content"**: most of the code and the description were written with generative AI. The description says so ("How it was made"). |
| **6.2.1 No AI images** | Icon, gallery and description images: in-game captures or hand-made only. |
| **6.2.2 Not primarily AI output** | As for Showtime: a reviewer may ask. Your design, direction, testing with the community and choices are your contribution; describe them honestly. |

## Dependencies to declare

| Mod | Type |
|---|---|
| Fabric API | Required |
| Cobblemon (1.8.1+) | Required |
| Cobblemon Battle Showtime (1.2.0+) | Required |
| Simple Voice Chat | Optional |
| Mod Menu | Optional |
| Cobblemon Battle Extras | Optional (compatible) |

No resource pack is required.

## The file

- Version: 0.1.0; `version` in `gradle.properties`, the same as the backend's
  `phantasmon.version.current`. Build with JDK 25: `./gradlew build`.
- Jar: `build/libs/phantasmon-client-<version>.jar` (not `-sources`). Rename it `phantasmon-<version>.jar` for the
  upload if you like.
- Version name: `Phantasmon <version>`. Channel: Beta.
- Game version 1.21.1, loader Fabric.

### Changelog

```
First public release.

- Ghost Pokémon: a second team of fully custom Pokémon, kept outside the Minecraft world
- PC with 16 boxes, full Pokémon cards, drag and drop, Showdown import
- Editor for every detail, checked for legality
- Send your Ghosts out with Cobblemon's animations; every Phantasmon player sees them
- Live trades through Cobblemon's interaction wheel
- Ghost battles in Cobblemon's engine: lobby, Showdown formats, hidden lead, turn timer, spectators,
  staged by Cobblemon Battle Showtime
- Hubs: meet, trade and battle with players of other servers; voice with Simple Voice Chat
- Rewards: auras, looks, reserved intros and ambiances
- French and English
```

## Gallery to prepare

In-game captures only, 1920×1080 if you can, HUD hidden (F1) where it helps:

1. The PC with a full team and a Pokémon card.
2. The editor with its live preview.
3. A Ghost following its player.
4. The trade screen.
5. The battle lobby (formats, red circles), then a moment of the battle.
6. A hub with players from two servers.
7. A Pokémon with an aura (rewards).

## Before you publish

- [ ] Showtime 1.2.0 is published.
- [ ] `./gradlew build` with JDK 25, then a check in a **clean instance**: Fabric API + Cobblemon + Showtime +
      Phantasmon only, no resource pack. It launches; with the backend unreachable it says so cleanly; with a test backend it
      logs in, opens the PC, sends a Ghost out and runs a battle against a second account.
- [ ] Follow `self-hosting.md` once on a blank machine (or a VM) to check that a stranger can do it.
- [ ] GitHub releases: `v<version>` on Phantasmon-Client with the jar, and the same tag on Phantasmon-Backend with the
      backend jar.
- [ ] Modrinth: create the project, upload the version, tick the disclosures. Reviewed before going public.
- [ ] CurseForge: create the project, upload the file. Reviewed before going public.
- [ ] Then put the real Modrinth and CurseForge links in the Phantasmon Events description.
