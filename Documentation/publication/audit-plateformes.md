# Audit de conformité : Modrinth et CurseForge (Phantasmon et Phantasmon Events)

> Fait le 2026-11-25, avant la première publication. Il confronte les deux mods, **tels qu'ils sont dans le code au
> 2026-11-25**, aux règles des deux plateformes et aux règles de Minecraft que CurseForge applique aussi. Les
> descriptions et listes de contrôle de publication datent du 2026-11-18 : plusieurs points ci-dessous viennent de ce
> qui a été ajouté depuis.
>
> Pages lues :
>
> - Modrinth : [Content Rules](https://modrinth.com/legal/rules) et [Terms of Use](https://modrinth.com/legal/terms),
>   version du 2026-08-13.
> - CurseForge : [Project Moderation Policies](https://support.curseforge.com/en/support/solutions/articles/9000197279-project-moderation-policies)
>   (modifiée le 2026-10-22), [Mod Authors Terms](https://legal.overwolf.com/docs/curseforge/mod-authors-terms)
>   (2025-08-05) et [Community Guidelines](https://legal.overwolf.com/docs/overwolf/community-guidelines/).
> - Minecraft : [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines). CurseForge applique l'EULA et
>   les conditions de l'éditeur du jeu, « pour le contenu comme pour les fonctions ». La page de l'EULA ne s'est pas
>   chargée : elle reste à relire.
> - Pokémon Reborn : [FAQ](https://www.rebornevo.com/pr/gamefaq/), question « can I use some of Reborn's resources? ».

## Verdict

**Pas en l'état.** Aucune règle n'interdit ce que font les deux mods : un backend externe, un compte, un mod utile
seulement avec un service. Mais plusieurs points doivent être corrigés avant de publier.

- **Les descriptions ne sont plus exactes.** Elles ne disent pas tout ce qui part vers le backend, et elles annoncent
  des commandes qui n'existent plus (Modrinth 1.7, 1.11, 2, 5.1). Pour Events, la phrase « It adds no other
  connection » est fausse depuis les hubs miroirs.
- **Les dépendances déclarées sont trop basses.** Elles disent « Showtime 1.2.0 ou plus », alors que le code
  appelle des fonctions bien plus récentes. Un joueur qui suit la page plante au lancement (Modrinth 5.1 et 5.6).
- **Deux fonctions relèvent des règles « triche » ou « EULA »** et demandent une décision : les blocs client des hubs
  (on marche dessus alors que le serveur ne les a pas) et la copie des zones des hubs miroirs.
- **Le contenu repris de Pokémon Reborn doit être crédité** selon le Readme de Reborn, qui reste à lire.
- **Il manque les mentions demandées par Minecraft** et un contact qui ne soit ni un chat ni un forum.
- **Modrinth refuse un projet dont le contenu est surtout produit par une IA (6.2.2).** C'est le plus gros risque
  de refus, à assumer en connaissance de cause.

CurseForge est le moins strict sur le réseau et sur l'IA. Modrinth est le plus précis sur la divulgation des envois
de données et sur l'IA.

## Suivi (mis à jour le 2026-11-25, après les décisions)

**Corrections faites** (pages, listes de contrôle, code ; pas encore publiées) :

| # | Fait |
|---|---|
| C1 | « Network and privacy » de Phantasmon : emotes, pose et micro, coups des combats relayés, chat des hubs gardé dans les journaux (5 Go par défaut), voix relayée et jamais enregistrée, blocs d'une zone copiée par un admin. |
| C2 | « a SHA-256 hash of its address rather than the address in clear (a hash of a known public address can still be matched to it) ». |
| C3 | Section « For admins » : ce qu'un admin peut faire aux joueurs (PC, Pokémon, combat, muet, verrou du chat, redémarrage). |
| C4 | La ligne fausse sur les commandes est remplacée par la vraie liste (`/phantasmon login`, `/phantasmon hub leave`, `/hc`). |
| C5 | **U** au lieu de **P** avec LeagueWay, dit aux deux endroits. |
| C6 | Events : section « Mirror hubs » et « Network and privacy » disent qui envoie quoi, et demandent l'accord des serveurs. |
| C7 | Events : sections League Reborn, hubs miroirs, jeu de trahison (vocal compris), pages web publiques ; journal des changements et galerie à jour. |
| C8 | `cobblemon_battle_showtime >=1.7.30 <2.0.0` dans les deux `fabric.mod.json`, les pages et les listes de contrôle : publier Showtime dans sa version actuelle d'abord. |
| C9 | Events : `phantasmon >=0.1.66 <0.2.0` ; Phantasmon se publie dans sa version courante (0.1.66 ou plus), plus en 0.1.0. |
| C10 | « NOT AN OFFICIAL MINECRAFT PRODUCT… » en gras à la fin des deux descriptions, et dans le `NOTICE` d'Events. |
| C11 | Ligne « Author: Mystaria. Contact: the issue tracker » dans les deux descriptions, avec le lien des tickets GitHub. Pas d'adresse e-mail (décision du 2026-11-25) : la règle vient des lignes directrices de Minecraft, que ni Modrinth ni CurseForge ne contrôlent (Showtime est passé sans contact) ; un suivi de tickets n'est ni un chat ni un forum. |
| C12 | Crédit Pokémon Reborn (Amethyst et l'équipe de Reborn, rebornevo.com) dans la description (« Credits »), le `NOTICE` et `fabric.mod.json` d'Events (description et `contributors`), avec « not affiliated with or endorsed by the Pokémon Reborn team ». Le Readme de Reborn ne demande aucune forme particulière ; il nomme l'équipe. Smogon crédité aussi. |
| C13 | « How it was made » des deux pages : icônes d'objets dessinées par des scripts écrits avec l'IA, placeholders du jeu de trahison. |
| D3 | Plus d'adresse de backend par défaut dans le code (`BackendUrlFile`) : fichier créé vide, aucune connexion tant que le joueur n'a rien saisi, H ouvre sur Réglages. |

**Décisions :**

| # | Décision |
|---|---|
| D1 | Garder les collisions des constructions de hub : LeagueWay fait pareil sans souci. |
| D2 | Une option côté joueur, **activée par défaut** : Réglages du menu H → « Synchronisation des terrains de hub » (`MirrorSharing`, `config/phantasmon-events.json`, `share_mirror_blocks`). Désactivée, `MirrorCapture` ne lit et n'envoie plus rien. La page d'Events le dit, avec « only set up a mirror hub on a server whose owners agree ». |
| D3 | Aucune adresse par défaut (fait, voir plus haut). |
| D4 | Aucune mention d'âge, comme Simple Voice Chat (sa page Modrinth n'en a pas). |
| D5 | Rien à trancher : le compte GitHub est celui de la personne, « Mystaria » le nom d'auteur sur Modrinth et CurseForge. |
| D6 | Cocher la case IA sur Modrinth et accepter un refus éventuel ; CurseForge n'a pas de règle équivalente. |

---

## 1. À corriger avant de publier

Toutes les corrections portent sur `description.md` et `release-checklist.md`, ici et dans
`Phantasmon-Events/Documentation/publication/`.

| # | Mod | Point | Règle | Ce qu'il faut faire |
|---|---|---|---|---|
| C1 | Phantasmon | La section « Network and privacy » est incomplète. Ce qui part vers le backend et n'y est pas dit (vérifié dans le code) : <br>- les **messages du chat des hubs**, que le backend garde dans ses journaux avec le pseudo (`HubService`, ligne `log.info("Hub chat <{}> …")`) ; <br>- la **voix** des hubs, relayée par le backend sur le WebSocket (D-36) ; <br>- les **emotes**, la pose (Crawl, Player Ladder), l'état du micro ; <br>- les **paquets de combat**, relayés entre les joueurs et les spectateurs ; <br>- les **blocs d'une zone** quand un admin copie une zone dans la construction d'un hub. | Modrinth 1.11, 2, 5.9 | Ajouter ces lignes à la liste. Dire combien de temps les journaux sont gardés (5 Gio, rotation) et que la voix n'est pas enregistrée. |
| C2 | Phantasmon | « a **hash** of its address (never the address itself) » : c'est un SHA-256 **sans sel** (`GhostSession.sha256Hex`). N'importe qui peut le comparer à une liste d'adresses de serveurs publics. | Modrinth 1.7 (pas d'affirmation trompeuse) | Écrire « a hash of its address, not the address in clear » sans laisser croire qu'il est impossible à retrouver, ou saler le hash côté backend. |
| C3 | Phantasmon | **Les pouvoirs des admins ne sont pas décrits.** Un admin du backend peut ouvrir le PC de n'importe quel joueur, y éditer ou supprimer ses Pokémon (`/phantasmon admin pc`), arrêter son combat, le rendre muet, redémarrer le backend. | Modrinth 2.1 (« critical information »), 1.7 | Une phrase dans « For admins » ou « Network and privacy ». |
| C4 | Phantasmon | « `/phantasmon` commands do everything the screens do » est **faux** depuis D-39 : il ne reste que `login`, `hub leave` / `chat`, `/hc` et les commandes admin. | Modrinth 1.7, 2 | Retirer la ligne ou la remplacer par la vraie liste. |
| C5 | Phantasmon | « **P** opens your PC » : c'est **U** quand LeagueWay est installé. | Modrinth 2 | Le préciser. |
| C6 | Events | « It adds no other connection » est **faux**. Les hubs miroirs (`hub/MirrorCapture`) font envoyer au backend, par **tout joueur Events** qui passe dans la zone source d'un hub miroir sur un serveur, les blocs de cette zone : tuiles entières (`EventsMirrorTile`), puis chaque changement (`EventsMirrorBlocks`), y compris les données des blocs (texte des panneaux, contenu lisible). Le monde solo de l'organisateur les reconstruit. | Modrinth 1.11 (envoi non choisi par le joueur : divulgation claire obligatoire), 1.7 | Le dire en clair dans « Network and privacy ». Mieux : une option pour refuser l'envoi (voir D2). |
| C7 | Events | La description du 2026-11-18 ne parle pas de **League Reborn**, des **hubs miroirs**, du **jeu de trahison** et de son vocal, ni du **site public**. | Modrinth 2 et 2.1, CurseForge « Clear and Informative Description » | Ajouter une section par partie et mettre à jour le journal des changements (CurseForge exige un changelog). |
| C8 | Les deux | **Dépendance à Showtime fausse.** Phantasmon déclare `cobblemon_battle_showtime >=1.2.0`, Events `>=1.4.0`, et les deux pages disent « 1.2.0 or later ». Les deux sont compilés contre Showtime 1.7.0 et appellent des fonctions ajoutées après 1.2 : `setAreaAmbiance` (1.4), les scènes et préréglages (1.6-1.7), `addDistantAmbianceOverride` (dernière version, utilisé par Events `reborn/DistantFields`). Avec une Showtime plus ancienne, le jeu plante (`NoSuchMethodError`). | Modrinth 5.1, 5.6 ; CurseForge (dépendances justes) | Publier Showtime d'abord dans sa version actuelle, puis mettre cette version comme minimum dans les deux `fabric.mod.json`, les pages et les listes de contrôle. |
| C9 | Events | Même problème pour Phantasmon. Events appelle des fonctions ajoutées après la 0.1.0 (`VirtualItems.register`, extensions Showdown, onglets du menu). | Modrinth 5.6 | Publier les deux à la même version, ou mettre le vrai minimum. |
| C10 | Les deux | **Mention Minecraft absente.** Les lignes directrices de Minecraft demandent d'afficher « en évidence » une mention du type « NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT » sur la page. Les descriptions ne citent que Nintendo, The Pokémon Company, Game Freak et Cobblemon. | Minecraft Usage Guidelines ; CurseForge (suit l'EULA) | Ajouter la phrase à la fin des deux descriptions. |
| C11 | Les deux | **Contact.** Les mêmes lignes directrices demandent de dire qui est responsable et comment le joindre, et précisent que « chat and forum links are not acceptable methods ». | Minecraft Usage Guidelines | Une adresse e-mail de contact du projet (une adresse du pseudonyme, pas la personnelle). |
| C12 | Events | **Crédit Pokémon Reborn.** Events contient : <br>- `league_reborn/fields.json`, les chiffres des terrains convertis depuis `fieldtext.rb` de Reborn ; <br>- `engine.js`, qui réimplémente les règles des terrains ; <br>- `ai/reborn_moves.json`, la table des capacités générée depuis le `movetext.rb` de Reborn ; <br>- `ai/RebornBattleAI.java`, une traduction ligne à ligne de `Battle_AI.rb`. <br>La FAQ de Reborn l'autorise : « As long as you credit for anything used per any specifics in the Readme, you can borrow whatever you need ». Le crédit est donc une **condition**. Aucune page ne le fait aujourd'hui. Les textes de Reborn (`reference-texts.json`) ne sont pas dans le jar, comme prévu. | Modrinth 1.3, 4 (crédit des sources) ; CurseForge « Content Copyright », Mod Authors Terms 4.1(vi) et (xi) | 1. Lire le Readme livré avec le jeu Reborn (la copie locale n'est plus sur le bureau) pour connaître la forme de crédit demandée. <br>2. Mettre ce crédit dans la description, dans `NOTICE` et dans `fabric.mod.json`. <br>3. Ajouter « not affiliated with the Pokémon Reborn team ». |
| C13 | Les deux | **IA.** Les listes de contrôle prévoient déjà de cocher « Contains AI-generated content » (Modrinth 6.1). Ajouts à faire : <br>- les **137 icônes d'objets** du client et les 5 d'Events sont dessinées par des scripts (`tools/held_item_icons.py`, `reborn_item_icons.py`), écrits avec l'IA : à compter comme « assets » générés dans la divulgation ; <br>- les placeholders du jeu de trahison (glyphes Zarbi, carte, document), générés par script, à remplacer ou à divulguer. | Modrinth 6.1, 6.2 | Le dire dans « How it was made ». Icône et galerie : seulement des captures en jeu et l'icône faite à la main. |

## 2. À décider

| # | Point | Pourquoi | Pistes |
|---|---|---|---|
| D1 | **Les blocs client des hubs ont une collision** (`HubBuilds` : « same rendering, lighting, shaders, collisions »). Un joueur Phantasmon peut monter sur une construction de hub que le serveur n'a pas. Pour le serveur, il marche ou tient en l'air, ce qui peut déclencher un anti-triche. Il peut aussi atteindre un endroit que les joueurs sans le mod n'atteignent pas. | Modrinth 3 range parmi les triches « flight, speed, or other movement modifications » sans activation par le serveur, et l'avantage injuste en multijoueur sans moyen pour le serveur de le refuser. CurseForge interdit aussi les avantages injustes (Community Guidelines). | a) Collision seulement là où le serveur a déjà un bloc plein, ou pas de collision au-dessus du vrai sol. <br>b) Un refus côté serveur : le mod n'affiche pas les hubs sur un serveur qui le signale (mot-clé dans le MOTD, ou liste d'empreintes refusées tenue par le backend à la demande d'un propriétaire de serveur). <br>c) Au minimum, le dire dans la description. **Recommandé : a)**, puis b) si possible. |
| D2 | **Copie des zones des serveurs (hubs miroirs)**, envoyée par tout joueur Events qui passe dans la zone. C'est l'équivalent d'un « world downloader » partiel. Beaucoup de serveurs l'interdisent dans leur règlement, et les constructions appartiennent à leurs auteurs. | Modrinth 1.3 et 1.11 ; CurseForge « Anti-Griefing » et Community Guidelines (« unfair advantages », respect de la loi). Rien ne l'interdit en toutes lettres, mais c'est le point le plus contestable des deux mods. | a) Une option côté joueur, « participer aux hubs miroirs », coupée par défaut ou demandée au premier passage. <br>b) Une règle dans la doc : l'organisateur pose un hub miroir seulement avec l'accord du serveur. <br>c) Le refus côté serveur de D1-b. <br>**Recommandé : a) + b)**, et le dire dans la description. |
| D3 | **Adresse du backend par défaut** : le serveur du projet (`server-linux.tail192969.ts.net`). Tout joueur qui installe le mod depuis Modrinth ou CurseForge envoie donc ses données à ce serveur. Celui qui l'héberge devient responsable de ces données au sens du RGPD : UUID, pseudo, positions, chat gardé dans les journaux, voix relayée. Les lignes directrices de Minecraft le disent aussi pour un serveur : « make it clear that you are responsible for all end user data privacy and protection requirements ». | Modrinth 1.4 (droits à la vie privée) ; CurseForge Mod Authors Terms 3.7 (« violates any applicable law ») ; RGPD | a) Garder ce défaut et publier une **politique de confidentialité** sur le site public (données, durée de conservation, droits, contact pour se faire supprimer). Il faut alors aussi un moyen de supprimer un compte : le backend n'en a pas aujourd'hui. <br>b) Ne mettre **aucune adresse par défaut** : le joueur saisit celle de sa communauté. <br>**Recommandé : b)** pour la version publique, sauf si tu veux accueillir des inconnus sur ton serveur. Le nom de la machine dans l'adresse en dit aussi un peu sur ton réseau. |
| D4 | **Âge.** Modrinth est ouvert dès 13 ans. En France, le consentement d'un mineur à un service en ligne vaut seul à partir de 15 ans. Le chat et la voix des hubs mettent en contact des inconnus de serveurs différents. | Lignes directrices Minecraft (contenu pour tout âge), RGPD | La politique de confidentialité du point D3 en parle, et les outils de modération du chat existent (muet, verrou). À noter dans la description. |
| D5 | **Publication liée à ton identité.** Les liens de source et de mise à jour pointent vers le compte GitHub `TrAdrienGit`, alors que l'auteur affiché est « Mystaria ». | Aucune règle : c'est pour toi. | Vérifie que ça te convient avant que les pages soient publiques. |
| D6 | **Refus possible sur Modrinth pour l'IA (6.2.2)** : « may not be published publicly if the contents are primarily or entirely a product of AI output ». Le code des deux mods est surtout écrit avec l'IA. Showtime est déjà en ligne sur Modrinth, ce qui montre que la modération l'a accepté une fois. | Modrinth 6.2.2. CurseForge n'a pas de règle équivalente : il demande seulement d'avertir pour une image modifiée par IA. | Décrire honnêtement ta part (conception, direction, tests avec la communauté) et accepter le risque. Ou publier d'abord sur CurseForge. |

## 3. Vérifié dans le code : conforme

| Point | Règle | Constat |
|---|---|---|
| Aucun code téléchargé ni exécuté depuis le réseau | Modrinth (logiciel malveillant), CurseForge 4.1(x) | Les scripts injectés dans le moteur Showdown (`ShowdownExtensions`) viennent du jar : `PreTerastallization.script()` dans le code, `league_reborn/engine.js` et `fields.json` dans les ressources d'Events. Le backend n'envoie que des données. |
| Jeton Mojang | Vie privée, sécurité | `AuthService` appelle `joinServer` sur la machine du joueur. Seuls l'UUID, le pseudo et le défi partent vers le backend (`AuthSessionRequestDto`) : le jeton ne quitte jamais le client. |
| Pas de contournement des blocages de Mojang | Modrinth 1.12 | Les hubs ne connectent à aucun serveur Minecraft : rien ne passe par le serveur de jeu. |
| Pas de mise à jour automatique | Aucune règle, mais bonne pratique | La poignée de main de version affiche seulement un lien vers les versions GitHub (`AuthService`). Rien n'est téléchargé. |
| Pas d'injection par le chat | Anti-griefing | Les messages des autres joueurs sont affichés en texte. Les seuls boutons cliquables lancent des actions enregistrées par le mod (`ChatActions`, préfixe vérifié), jamais une commande reçue du réseau. |
| Paquets de combat relayés filtrés | Sécurité | `RelayedPacketPolicy` n'accepte que `cobblemon:battle_*` et `phantasmon:action_effect` (SEC-2). |
| Combats Ghost | Modrinth 3, CurseForge (triche) | Ils ne touchent ni au jeu du serveur ni à ses vrais Pokémon : aucun avantage sur le serveur. |
| Pas de musique ni d'asset de jeu dans les jars | CurseForge « Licensed Music », Modrinth 1.3 | Les trois sons du PC ont été vérifiés le 2026-11-18. Les icônes de type viennent du Cobblemon du joueur au lancement. Les icônes d'objets sont dessinées par nos scripts (ni extraites ni copiées). Les packs de musique ne sont ni dans les jars ni liés depuis les pages, et ne doivent pas l'être. |
| Pas de vente ni de monnaie réelle | Lignes directrices Minecraft (mods), CurseForge (publicité) | Les récompenses sont données par un admin, jamais achetées. Aucune publicité dans le mod. |
| Licence et métadonnées | Modrinth 5.1 | Apache-2.0 dans les deux `fabric.mod.json`, environnement `client` (juste : le `mirror-server` d'Events est un projet à part, non publié). |
| Noms | CurseForge « Name » | « Phantasmon », « Phantasmon Events » : ni le nom du jeu, ni un numéro de version. |
| Liens de téléchargement externes | CurseForge « Third Party Downloads » | Les pages ne lient que des sources et des guides. **Ne pas lier** la page `/download` du site, ni des jars sur GitHub, dans la description CurseForge. |
| Icônes | Modrinth 6.2.1, CurseForge « Project Avatar » | Faites à la main (décision du 2026-11-18), 1024 × 1024 : CurseForge les redimensionne en 400 × 400. Éviter le webp. |

## 4. À vérifier en plus, hors code

- Lire l'**EULA de Minecraft** (la page ne s'est pas chargée pendant cet audit) pour confirmer qu'elle n'ajoute rien
  aux lignes directrices.
- ~~Le **Readme de Pokémon Reborn** (point C12)~~ : lu le 2026-11-25 (§ VII Credits), aucune forme de crédit
  imposée.
- Le **logo de la Ligue** (`textures/intro/league.png`) : vérifié le 2026-11-18 (« fine to publish »).
- ~~Créditer Smogon~~ : fait dans la description et le `NOTICE` d'Events (2026-11-25).
