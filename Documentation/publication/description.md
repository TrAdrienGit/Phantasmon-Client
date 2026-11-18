# Phantasmon

**Your own Pokémon team, on any Cobblemon server: build it, send it out, trade it and battle with it, across servers.**

Phantasmon gives every player a second team of **Ghost Pokémon**, built exactly as you want them (species, form,
shiny, ability, nature, item, moves, IVs and EVs, Tera type) and kept outside the Minecraft world. Send your Ghosts
out to follow you, trade them, and battle other players with them, in Cobblemon's own battle engine, under Smogon's
rules if you like. Meet players from other servers in shared hubs.

**Client-side only, but not standalone.** Nothing to install on the Minecraft server: Phantasmon works on any
Cobblemon server, and in single player. Your data lives on a **Phantasmon backend**, a separate program that a
community hosts for its players (see [Who is it for](#who-is-it-for)). Only players with the mod see the Ghosts.

<!-- Screenshot: the PC with a team and the Pokémon card -->

## Who is it for

Phantasmon was made for a Cobblemon community, which hosts its own backend. You can use it in two ways:

- **Join a community that runs Phantasmon:** install the mod and you are ready, if your community's address is
  already set (or it gives you the one to enter in the settings).
- **Run it for your own community:** host the backend yourself (free and open source, a small Java program and a
  PostgreSQL database), then give your address to your players. The guide is linked below.

Without a backend to connect to, the mod does nothing.

## A PC of your own

Press **P**. Your team and 16 boxes of 30, with a full card for each Pokémon (3D model, types, item, nature, ability,
Tera type, moves, IVs and EVs). Drag and drop between any slots. Import a team straight from a Pokémon Showdown paste.

## An editor for every detail

Nickname, gender, level, shiny, ability, held item, nature, Tera type, IVs and EVs, four moves, all with a live
preview. Paste a Showdown set to fill it in. Every value is checked by the backend: no illegal stats.

<!-- GIF: the editor -->

## Ghosts that follow you

Cobblemon's party key (**O**) now also shows your Ghost team; pick a Ghost and press **R** to send it out with
Cobblemon's own Poké Ball animation. It follows you, roams when you stand
still, and every other player with Phantasmon sees it, with Cobblemon's models, forms and shininess.

## Trade

Look at a player and pick **Ghost Trade** in Cobblemon's interaction wheel (**R**). Both of you see each other's team,
make an offer, confirm, and both Pokémon change hands at once.

## Battle

Pick **Ghost Battle** in the wheel. A lobby opens on both screens:

- your Ghost team or your Cobblemon party,
- a battle format: free, or Pokémon Showdown's own rules (National Dex, Gen 9 tiers…), with each Pokémon that breaks
  them circled in red and the reason,
- your lead, kept hidden from your opponent,
- an optional turn timer.

The battle runs in Cobblemon's own engine, on one player's game, with Cobblemon's battle screen and animations. Other
players can watch it. Phantasmon stages its battles with
[Cobblemon Battle Showtime](https://modrinth.com/mod/cobblemon-battle-showtime): cinematic camera, intros, battle
ambiances and your own music, picked together in the lobby.

<!-- GIF: a Ghost battle -->

## Hubs between servers

Hubs bring together players from **different Minecraft servers**, with nothing installed on any of them. A player
places an **Anchor** (a 21-block zone) on their server; the hub's build appears in it for every Phantasmon player of
that server. Step in, and you see the players who came in through other servers' anchors, with their skins and
Ghosts: talk with `/hc`, trade, battle, watch. Your real coordinates and your server's address are never shared.
With [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), you hear the hub's players by their avatar.

<!-- Screenshot: a hub with players from two servers -->

## Rewards and looks

Communities can reward their players: auras and looks on their Pokémon, reserved battle intros and ambiances. Admins
give them from the game.

## For admins

Admins (listed in the backend) get the admin menu in game: players, hubs and their builds, rewards, a solo battle to
test a team. Everything is done from the game, nothing on the Minecraft server.

## How to use

- **P** opens your PC, **H** the Phantasmon menu (hubs and your anchors, rewards, music, settings including the
  backend address). Change them in Options → Controls → Phantasmon.
- **O** (Cobblemon's party key) cycles to your Ghost team; **R** sends the picked Ghost out or recalls it.
- **R** (Cobblemon's wheel) on a player: Ghost Trade, Ghost Battle, Watch the battle.
- `/phantasmon` commands do everything the screens do.
- French and English.

## Network and privacy

Phantasmon is an online mod: it needs a backend, and it talks to it while you play.

- **The backend** (the address in Settings, `config/phantasmon.json`): by default, **the backend of the Phantasmon
  community that made this mod**. Change it to your own community's. The mod sends it:
  - your Minecraft UUID and username (your account there);
  - your Pokémon, trades, battles and their results;
  - while you play: which server you are on, as a **hash** of its address (never the address itself), your
    dimension, and your position, so that players on the same server and dimension see your Ghost; in a hub, only
    your position inside the hub's zone.
- **Mojang**: to log in, the mod proves your Minecraft session to Mojang's session server, the same check a Minecraft
  server makes; your password and tokens never reach the backend. Player skins come from Mojang as in vanilla.
- Nothing is sent when no backend is reachable, and the backend owner only sees what is listed above.

## Dependencies

**Required mods**

| Mod | Version |
|---|---|
| [Fabric Loader](https://fabricmc.net/use/) | 0.18.1 or later, Minecraft 1.21.1, Java 21 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | any for 1.21.1 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.8.1 or later |
| [Cobblemon Battle Showtime](https://modrinth.com/mod/cobblemon-battle-showtime) | 1.2.0 or later (1.x) |

**Optional mods**

| Mod | Adds |
|---|---|
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Voice in the hubs (also needed on the server or in the single-player world). |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Opens the Phantasmon menu from the mod list. |
| [Cobblemon Battle Extras](https://modrinth.com/mod/cobblemon-battle-extras) | Works alongside. |

**Also needed:** a premium (Microsoft) Minecraft account, and a Phantasmon backend.

## Host your own backend

The backend is free and open source (Java 21 + PostgreSQL). One backend serves a whole community, on as many
Minecraft servers as you like. Step-by-step guide: build, database, settings, HTTPS, players' address, updates:
**https://github.com/TrAdrienGit/Phantasmon-Backend/blob/main/Documentation/guides/self-hosting.md**

Add-on: [Phantasmon Events](https://modrinth.com/mod/phantasmon-events) adds event NPCs, tournaments and community
games on top of Phantasmon.

## How it was made

Phantasmon was developed with the help of generative AI coding tools, under its author's direction, design and testing
in game with the community. Pokémon models, animations and battles come from each player's own Cobblemon; the mod
contains no game assets and no music.

## Links

- Mod source and issues: https://github.com/TrAdrienGit/Phantasmon-Client
- Backend source: https://github.com/TrAdrienGit/Phantasmon-Backend
- License: Apache 2.0

*A fan project, not affiliated with Nintendo, The Pokémon Company, Game Freak or the Cobblemon team.*
