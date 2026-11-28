# Guide du joueur

Phantasmon ajoute des **Ghost Pokémon** : des Pokémon que vous créez librement (espèce, niveau, talent, attaques,
IV/EV…), rangés dans un PC à part, que vous pouvez faire sortir à vos côtés, échanger et faire combattre avec
d'autres joueurs qui ont le mod. Ils n'ont aucun effet sur vos vrais Pokémon Cobblemon : pas d'expérience, pas
d'évolution, pas de combat contre des Pokémon sauvages.

Liste complète des commandes et touches : [`reference/commands-and-keybinds.md`](../reference/commands-and-keybinds.md).

## 1. Connexion

Automatique en entrant dans un monde si le service Phantasmon est disponible. Sinon, `/phantasmon login`.

- Si votre version du mod est trop ancienne, un message vous le dit avec un lien de téléchargement.
- Un compte hors-ligne n'est pas accepté.

## 2. Le PC (touche P, ou U si LeagueWay est installé)

- **À gauche**, votre équipe (6 emplacements) ; **au centre**, la fiche du Pokémon sélectionné ; **à droite**, la
  boîte affichée (16 boîtes de 30 cases).
- **Cliquer** sélectionne un Pokémon. **Glisser-déposer** le déplace ; si la case est occupée, les deux Pokémon
  échangent leur place (PC ou équipe, peu importe).
- **Double-clic** : un Pokémon d'une boîte rejoint l'équipe (premier emplacement libre) ; un Pokémon de l'équipe
  part dans le premier emplacement libre du PC (boîte 1 d'abord). Équipe ou PC plein : message en bas, rien ne bouge.
- **Changer de boîte** : molette, flèches ← →, ou ◀ ▶. Fonctionne aussi pendant un glisser, pour déposer dans une
  autre boîte.
- **Boîte équipes** (bouton BOÎTE VRAC / BOÎTE ÉQUIPES en haut de la boîte) : une équipe par ligne. Double-clic sur
  un Pokémon de la ligne : la ligne et votre équipe s'échangent. Au-dessus de chaque ligne :
  - son **nom** : cliquez dessus pour le changer (Entrée garde, Échap annule, vide = « Équipe n ») ;
  - son **format** (« + Format ») : cliquez pour en choisir un dans la même liste que le lobby, clic droit pour
    l'enlever. La ligne est alors vérifiée : « ✔ Valide », ou « ✕ n invalide(s) » avec les Pokémon fautifs entourés
    en rouge ; survolez-en un (ou sélectionnez-le) pour lire pourquoi, par-dessus le modèle de la fiche.
- **IMPORTER** : copiez un ou plusieurs Pokémon au format Pokémon Showdown (depuis Showdown, un calculateur de
  dégâts…), puis cliquez. Ils arrivent dans les premières cases libres.
- **ÉDITER** ouvre l'éditeur. **SUPPRIMER** (ou Suppr) demande confirmation : la suppression est définitive.

Exemple à copier :

```text
Bichou (Samurott-Hisui) @ Assault Vest
Ability: Torrent
Shiny: Yes
Tera Type: Grass
EVs: 144 Atk / 64 Def / 136 SpD
Timid Nature
- Avalanche
- Aqua Tail
- Body Slam
- Dark Pulse
```

## 3. L'éditeur

À gauche, la fiche se met à jour pendant que vous modifiez le formulaire de droite :

- surnom, sexe (si l'espèce le permet), niveau, chromatique ;
- talent (parmi ceux de l'espèce), objet tenu (objets de combat, avec recherche), nature, Téracristal ;
- IV (0-31) et EV (0-252, 510 au total : le total passe en rouge au-delà), Puissance Cachée calculée ;
- 4 attaques, avec recherche, sans doublon.

La recherche accepte les mots dans n'importe quel ordre (« booster energy » trouve « Energy Booster »).
**IMPORTER** remplit le formulaire depuis le presse-papiers sans enregistrer. **ENREGISTRER** (ou Entrée)
sauvegarde ; Échap ferme (avec confirmation s'il reste des modifications).

## 4. Faire sortir un Ghost

Appuyez sur **O** (touche « Cacher l'équipe » de Cobblemon) jusqu'à afficher l'équipe Phantasm à gauche de l'écran,
choisissez un Ghost avec **haut / bas**, puis **R** le fait sortir avec l'animation de Poké Ball ; **R** à nouveau le
rappelle. Il vous suit, se promène autour de vous quand vous restez immobile, et rentre
automatiquement si vous mourez, changez de dimension ou vous déconnectez. Les joueurs qui ont le mod et sont
dans la même dimension du même serveur le voient aussi.

Pour sortir un autre Pokémon, placez-le d'abord en emplacement 1 dans le PC.

## 5. Échanger

1. Visez un joueur et ouvrez la roue de Cobblemon (**R**) → « Échange Ghost ».
2. L'autre joueur clique **[Accepter]** dans le chat (60 s pour répondre).
3. Chacun choisit son offre dans son équipe (rail de gauche) et voit celle de l'autre à droite.
4. Cliquez **⇄ ÉCHANGER** pour vous déclarer prêt. Si l'un change d'offre, il faut se redéclarer prêt.
5. Quand les deux sont prêts, l'échange se fait : le Pokémon reçu prend la place de celui que vous avez donné.

**QUITTER** (ou Échap) annule l'échange pour les deux. Seuls les Pokémon de l'équipe sont échangeables.

## 6. Combattre

1. Visez un joueur et ouvrez la roue de Cobblemon (**R**) → « Combat Ghost ».
2. L'autre joueur clique **[Accepter]** : l'écran de préparation s'ouvre chez les deux. Vous y voyez votre équipe et
   seulement les modèles et noms des Pokémon adverses. Choisissez vos Ghost ou votre équipe Cobblemon (bouton
   ⇄), choisissez ensemble le **format** (bouton en bas à gauche : il ouvre la fenêtre des formats, voir plus bas — les Pokémon qui ne
   le respectent pas sont entourés en rouge, survolez les vôtres pour savoir pourquoi), cliquez le Pokémon à envoyer en premier (votre lead, que l'adversaire ne voit pas), puis **PRÊT**. Le combat
   commence quand les deux joueurs sont prêts. **Timer** (en bas) : 150 s, ensuite le premier Pokémon est choisi
   pour qui n'est pas prêt, et le chrono de combat s'active.
3. Le combat utilise l'interface de Cobblemon, avec ses animations. Vos Pokémon apparaissent devant vous.
4. Facultatif : **[Activer le chrono]** dans le chat impose 90 s par choix aux deux
   joueurs, jusqu'à la fin du combat. Passé ce délai, une action est jouée automatiquement.

Pendant le combat, la caméra filme le match (plans autour du terrain, plans sur les attaques). Les boutons à
droite de l'écran de combat passent à la musique suivante, basculent caméra cinéma / libre et activent le chrono
(qui devient alors le compte à rebours).

**Nouvelles Méga-Évolutions** (Légendes Z-A / Champions, D-42) : 49 formes, dont Méga-Mélodelfe, Méga-Dracolosse,
Méga-Raichu X/Y et Méga-Absol Z. Donnez sa Méga-Gemme au Pokémon dans l'éditeur (elle s'importe aussi d'un set
Showdown) ; elles ne marchent que dans les combats Phantasmon, en format « Libre » ou Champions. Pour voir leurs modèles, activez
le pack de ressources « Phantasmon Megas » (sans lui, le Pokémon garde son modèle normal ; Heatran, Darkrai,
Zeraora, Tatsugiri, Magearna et Zygarde n'ont pas encore de modèle).

**Formats Champions** (Champions OU, Champions UU, Champions National Dex) : les équipes suivent les règles de
Pokémon Champions (Pokémon, objets, attaques et talents disponibles dans ce jeu, niveau 50, un même objet une seule
fois, pas de Téracristallisation). Le combat lui-même reste celui de la Gen 9 : vos EV comptent comme d'habitude,
pas de points de stats à la Champions.

**Fenêtre des formats** : 53 formats Showdown compatibles avec le moteur, en quatre colonnes — **Simple**, **Double**,
**Aléatoire**, **Autres** (Libre et les « Other Metagames » : AAA, STABmons, Monoletter, Balanced Hackmons, Monocolor,
NFE…). Chaque colonne est rangée en sous-groupes (National Dex d'abord, puis Gen 9, puis Champions ; les
aléatoires en Simple / Double), du plus joué au moins joué. Chaque carte montre ses
étiquettes : NatDex, Champions, niveau imposé, « 3 sur 6 » (Pokémon envoyés au combat), équipes aléatoires. Molette
sur une colonne (ou sa barre, attrapée à la souris) pour la faire défiler, clic pour choisir, Échap pour revenir.

**Barre du bas du lobby** : à gauche les règles du combat, **Format** et **Terrain** (League Reborn) ; puis son
apparence, **Scène** (scènes complètes stade + ambiance, ou un stade seul), **Ambiance** et **Intro** ; à droite
Musiques, Timer, Quitter. Chaque bouton ouvre sa fenêtre : des cartes avec, en fond, une image d'aperçu du choix
(sans image, un fond coloré à son nom). Un bouton grisé est imposé (PNJ d'événement, ou vous êtes prêt). Les
erreurs et avertissements (Pokémon hors format, refus du serveur, génération d'une équipe aléatoire) s'affichent
dans un encadré au milieu de votre fenêtre de joueur.

**Cadre du lead** : en simple, le modèle, le nom, le niveau, l'objet, le talent et la nature à gauche, les quatre
attaques (couleur de leur type) et la répartition des EV (stat montée par la nature en rouge, baissée en bleu) à
droite ; en double, chaque lead avec ses attaques et ses EV sur une ligne. **Badges** : à côté de chaque joueur,
3 colonnes de 6 emplacements pour ses badges de ligue (ils viendront de Leagueway ; pour tester, le fichier
`phantasmon/badges-mock.json` du dossier du jeu, `{"pseudo": ["phantasmon:textures/gui/badges/mock_flame.png", …]}`,
relu toutes les 5 secondes ; chaque badge : `texture`, `name`, `leader`, `type`). Survoler un badge l'affiche en grand avec
son nom, son champion d'arène et son type. Le bouton PRÊT (au centre de l'en-tête, rectangulaire, liseré bleu qui respire tant qu'il est cliquable, vert une fois
prêt) s'illumine et crépite au survol, et éclate au clic. Les Pokémon du lobby jouent leur animation de repos (ceux qui
n'en ont pas flottent doucement de haut en bas) et jouent leur animation de cri (sans le son) quand la souris entre sur leur case ou la clique.

**Images d'aperçu** : mettez en place ce que vous voulez photographier (un combat avec ce terrain, ce stade, cette
ambiance, une intro qui joue), puis tapez `/phantasmon preview <field|preset|stadium|ambiance|intro> <id>` : le chat
se ferme, l'interface se cache un instant et l'image (640×360, le milieu de l'écran) est enregistrée dans
`phantasmon/previews/<type>/<id>.png` du dossier du jeu. Les fenêtres l'affichent aussitôt. Pour la livrer avec le
mod, copiez-la dans `src/main/resources/assets/phantasmon/textures/gui/previews/<type>/` (un id `ns:nom` devient
le dossier `ns/nom`).

**Formats aléatoires** (Random Battle, Random Doubles, Monotype Random, Hackmons Cup, Doubles Hackmons Cup) : vos
équipes ne servent pas : dès que le format est choisi, Showdown vous génère six Pokémon (sets et niveaux de Showdown, comme
sur Showdown), que vous voyez dans le lobby et dont vous choisissez le lead (ou les Pokémon, en double). L'équipe adverse reste
cachée (« ? »). Rechoisir un format aléatoire génère une nouvelle équipe. Pas de version National Dex ni Champions : le moteur de Cobblemon ne les a pas.

**Battle Stadium Singles** (BSS Reg I, Champions BSS) : 6 montrés, **3 choisis** dans l'ordre (le 1 commence), niveau 50.

**Combat double** (Doubles OU, Doubles Ubers, Doubles UU, National Dex Doubles, Champions Doubles, VGC…, colonne
Double) : deux Pokémon par côté. Dans l'écran de préparation, cliquez vos Pokémon **dans l'ordre** :
le 1 commence à gauche, le 2 à droite, les suivants attendent sur le banc dans cet ordre ; recliquer un Pokémon le
retire (les suivants remontent). Ceux que vous ne choisissez pas suivent dans l'ordre de l'équipe. **Champions VGC**
se joue comme en tournoi officiel : 6 Pokémon montrés, **4 choisis** pour le combat, niveau 50, sans légendaires
restreints ni fabuleux ; **VGC 2024 Reg G** en autorise un, **VGC 2025 Reg I** deux. En combat,
l'interface de Cobblemon demande une action pour chacun de vos deux Pokémon, puis la cible. Les PNJ de Phantasmon Events
peuvent aussi être réglés sur un format double ; pas encore de tournoi en double.

**Musiques** (jouées par Cobblemon Battle Showtime depuis D-37) : installez le pack de ressources « Cobblemon Battle Showtime
Music » (modèle dans `resourcepack-template/` du dépôt `Cobblemon-Battle-Showtime`) et déposez vos `.ogg` dans ses dossiers
`lobby`, `intro`, `battle`, `victory`, `defeat` (voir son `LISEZ-MOI.txt`) ; un ancien pack « Phantasmon Music »
marche toujours. Les morceaux décochés sont lus dans `config/phantasmon-music.json` puis enregistrés dans
`config/cobblemon_battle_showtime-music.json`. Elles sont tirées au hasard ; F3 + T recharge après un ajout. La touche **N**, le bouton **♪ Musiques** du lobby ou
Mod Menu → Phantasmon → Configurer ouvrent le menu des musiques, qui permet de décocher les morceaux qu'on ne veut pas entendre et d'écouter chacun.

**Regarder un combat** : visez un joueur en combat Ghost (ou son avatar dans le Global Hub), **R** → « Regarder le
combat Ghost ». L'écran de combat de Cobblemon s'ouvre en mode spectateur et les Pokémon apparaissent devant les deux
dresseurs ; le bouton Retour arrête. Les joueurs voient « X regarde le combat ».

Abandonner donne la victoire à l'adversaire ; une déconnexion annule le combat sans vainqueur. Les Pokémon
retrouvent leur état normal après le combat.

## 7. Le Global Hub (Phantasmon Network)

Le Global Hub réunit des joueurs Phantasmon de **serveurs Minecraft différents**, sans rien installer sur les
serveurs. On y entre par un **Anchor** : une zone de 21 × 21 × 21 blocs posée par un joueur. Tous les joueurs
Phantasmon de ce serveur la voient et peuvent l'utiliser ; ceux d'un autre serveur ne la voient jamais.

1. **Poser un Anchor** : il existe plusieurs hubs, créés par les admins. Touche **H** : le gestionnaire des hubs
   liste les hubs (taille, construction, date, nombre d'ancres) et vos ancres vers chacun. Choisissez un hub, PLACER
   UNE ANCRE, un nom facultatif, PLACER : le menu se ferme et la zone du hub vous suit, à vos pieds, tournée selon votre
   regard (au quart de tour) ; verte si elle est libre, rouge avec le nombre de blocs gênants sinon. Placez-vous, ouvrez
   le chat (T) et cliquez sur **[Accepter]** pour la poser là où vous êtes, ou **[Annuler]**. La zone du hub, de vos pieds vers le haut,
   doit être entièrement vide (air) et ne pas toucher un autre Anchor : la **construction du hub** (salle, arène…)
   s'y bâtit aussitôt pour tous les joueurs Phantasmon du serveur. Ses blocs n'existent que pour Phantasmon : on ne
   peut ni les casser ni poser de bloc dessus ; les portes s'ouvrent. Chacun peut poser un Anchor par hub ; pour le
   déplacer, supprimez-la (✕ dans le gestionnaire) puis reposez-la (la construction disparaît avec l'Anchor). Chaque
   hub est un espace à part : on n'y voit que les joueurs entrés par un Anchor du même hub.
   **Vocal** : avec le mod Simple Voice Chat (facultatif), on entend les joueurs du hub depuis leur avatar et ils vous
   entendent, avec vos réglages habituels de Simple Voice Chat (touche pour parler, activation vocale, muet). Il faut
   que Simple Voice Chat soit aussi installé sur votre serveur (ou votre monde solo) ; sinon un message l'indique en
   entrant et vous êtes dans le hub sans vocal. Le volume des voix du hub se règle dans le menu de Simple Voice Chat
   (catégorie « Hub Phantasmon »).
2. **Le repérer** : un carré de particules violettes marque le sol de chaque Anchor de votre serveur et de votre
   dimension, avec son nom au-dessus du centre.
3. **Entrer** : en entrant dans le carré, le chat propose **[Oui]**, **[Non]** ou **[Toujours ici]**. Rien n'est
   partagé tant que vous n'avez pas accepté. Le Hub accueille 50 joueurs au plus.
4. **Dans le Hub** : vos déplacements dans l'Anchor sont partagés, relativement à son centre (jamais vos vraies
   coordonnées ni l'adresse du serveur). Les arrivées et départs s'affichent dans le chat. Les joueurs des autres
   serveurs apparaissent dans l'Anchor avec leur skin et leur pseudo suivi de `[Hub]` ; ils marchent, sautent et
   s'accroupissent comme eux, avec leur cape et la couche supérieure de leur skin ; on se pousse en se rentrant
   dedans, comme entre joueurs, mais on ne peut pas les frapper. Un joueur de
   votre serveur entré par un autre Anchor apparaît aussi en avatar ; dans le même Anchor que vous, vous le voyez
   simplement pour de vrai.
5. **Parler** : `/hc <message>`. Les messages du Hub s'affichent préfixés `[Hub]` et ne passent jamais par le
   serveur Minecraft.
6. **Ghost** : un Ghost que vous sortez dans le Hub (ou déjà sorti en y entrant) suit votre avatar chez les autres
   joueurs, comme il vous suit chez vous.
7. **Échanger et combattre** : visez l'avatar d'un joueur et appuyez sur **R** : la roue propose « Échange Ghost » et
   « Combat Ghost », comme avec un joueur de votre serveur. Restez dans l'Anchor pendant la partie : en sortir vous
   fait quitter le Hub et l'autre ne vous voit plus (la partie continue).
8. **Sortir** : sortez du carré, ou `/phantasmon hub leave`. Changer de serveur ou de dimension, ou se déconnecter,
   fait aussi sortir du Hub.

## 8. En cas de problème

Voir [`troubleshooting.md`](troubleshooting.md).
