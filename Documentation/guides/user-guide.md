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

## 2. Le PC (touche P)

- **À gauche**, votre équipe (6 emplacements) ; **au centre**, la fiche du Pokémon sélectionné ; **à droite**, la
  boîte affichée (16 boîtes de 30 cases).
- **Cliquer** sélectionne un Pokémon. **Glisser-déposer** le déplace ; si la case est occupée, les deux Pokémon
  échangent leur place (PC ou équipe, peu importe).
- **Double-clic** : un Pokémon d'une boîte rejoint l'équipe (premier emplacement libre) ; un Pokémon de l'équipe
  part dans le premier emplacement libre du PC (boîte 1 d'abord). Équipe ou PC plein : message en bas, rien ne bouge.
- **Changer de boîte** : molette, flèches ← →, ou ◀ ▶. Fonctionne aussi pendant un glisser, pour déposer dans une
  autre boîte.
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
   ⇄), choisissez ensemble le **format** (liste en bas à gauche : Libre, National Dex, Gen 9 — les Pokémon qui ne
   le respectent pas sont entourés en rouge, survolez les vôtres pour savoir pourquoi), cliquez le Pokémon à envoyer en premier (votre lead, que l'adversaire ne voit pas), puis **PRÊT**. Le combat
   commence quand les deux joueurs sont prêts. **Timer** (en bas) : 150 s, ensuite le premier Pokémon est choisi
   pour qui n'est pas prêt, et le chrono de combat s'active.
3. Le combat utilise l'interface de Cobblemon, avec ses animations. Vos Pokémon apparaissent devant vous.
4. Facultatif : **[Activer le chrono]** dans le chat (ou `/phantasmon battle timer`) impose 90 s par choix aux deux
   joueurs, jusqu'à la fin du combat. Passé ce délai, une action est jouée automatiquement.

Pendant le combat, la caméra filme le match (plans autour du terrain, plans sur les attaques). Les boutons à
droite de l'écran de combat passent à la musique suivante, basculent caméra cinéma / libre et activent le chrono
(qui devient alors le compte à rebours).

**Musiques** : installez le pack de ressources « Phantasmon Music » (modèle dans `resourcepack-template/` du dépôt
client) et déposez vos `.ogg` dans ses dossiers `lobby`, `intro`, `battle`, `victory`, `defeat` (voir son
`LISEZ-MOI.txt`). Elles sont tirées au hasard ; F3 + T recharge après un ajout. La touche **N**, le bouton **♪ Musiques** du lobby ou
Mod Menu → Phantasmon → Configurer ouvrent le menu des musiques, qui permet de décocher les morceaux qu'on ne veut pas entendre et d'écouter chacun.

Abandonner donne la victoire à l'adversaire ; une déconnexion annule le combat sans vainqueur. Les Pokémon
retrouvent leur état normal après le combat.

## 7. Le Global Hub (Phantasmon Network)

Le Global Hub réunit des joueurs Phantasmon de **serveurs Minecraft différents**, sans rien installer sur les
serveurs. On y entre par un **Anchor** : une zone de 21 × 21 × 21 blocs posée par un joueur. Tous les joueurs
Phantasmon de ce serveur la voient et peuvent l'utiliser ; ceux d'un autre serveur ne la voient jamais.

1. **Poser un Anchor** : placez-vous au centre d'une zone plate et dégagée, regardez dans la direction qui sera
   « l'avant » du Hub, puis `/phantasmon hub anchor create <nom>`. Chacun peut en poser un seul ; pour le déplacer,
   `/phantasmon hub anchor delete` puis recréez-le.
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
7. **Sortir** : sortez du carré, ou `/phantasmon hub leave`. Changer de serveur ou de dimension, ou se déconnecter,
   fait aussi sortir du Hub.

## 8. En cas de problème

Voir [`troubleshooting.md`](troubleshooting.md).
