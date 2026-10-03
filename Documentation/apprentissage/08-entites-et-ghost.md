# 8. Entités et Ghost

## 8.1 Qu'est-ce qu'une entité

Tout ce qui bouge dans Minecraft (joueurs, animaux, flèches, Pokémon de Cobblemon) est une **entité** (`Entity`) :
position, rotation, vitesse, hitbox, et un `tick()` appelé 20 fois par seconde. Une entité vit dans un **niveau**
(`Level`, le monde d'une dimension).

Normalement :

1. le **serveur** crée l'entité dans son `ServerLevel` et la fait vivre (IA, physique) ;
2. il envoie des paquets aux clients proches : « une entité n°123 de type X est apparue ici », puis ses mouvements ;
3. chaque client crée une **copie** dans son `ClientLevel` et la dessine.

Un rendu d'entité (*renderer*) lit l'état de la copie client pour dessiner le modèle, jouer l'animation de marche,
etc.

## 8.2 Une entité qui n'existe que chez nous

Phantasmon n'a pas de serveur Minecraft à sa disposition. Il saute les étapes 1 et 2 : **chaque client crée
lui-même** l'entité dans son monde local, à partir des messages du backend.

```java
// ghost/GhostEntityManager.java (raccourci)
Species resolvedSpecies = PokemonSpecies.INSTANCE.getByName(species);       // données Cobblemon locales
if (resolvedSpecies == null) { LOG.warn("Cannot render Ghost: unresolved species '{}'", species); return; }

Pokemon pokemon = new Pokemon();                                            // l'objet « Pokémon » de Cobblemon
pokemon.setSpecies(resolvedSpecies);
pokemon.setShiny(shiny);
pokemon.setLevel(Math.max(1, level));
…
PokemonEntity entity = new PokemonEntity(clientLevel, pokemon, CobblemonEntities.POKEMON);   // l'entité Cobblemon
entity.setNoAi(true);              // aucune IA : il ne doit jamais décider seul de bouger ou d'attaquer
entity.setInvulnerable(true);
entity.noPhysics = true;           // ne pousse pas et n'est pas poussé (voir 8.5)
entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
clientLevel.addEntity(entity);     // ajoutée directement au monde local, aucun serveur impliqué
```

On réutilise **la classe d'entité de Cobblemon** (`PokemonEntity`) : son renderer, ses modèles et ses animations
fonctionnent gratuitement. Cobblemon a une méthode `Pokemon.sendOut(...)`, mais elle exige un `ServerLevel` : on ne
peut pas l'utiliser.

## 8.3 Les données synchronisées

Une entité a des **données synchronisées** (`SynchedEntityData`) : des valeurs que le serveur envoie
automatiquement au client, et que le renderer lit. Pour une `PokemonEntity` : les **aspects** (variante visuelle),
« en mouvement », la pose (debout, marche, vol), le mode du faisceau de Poké Ball…

Sur un serveur, c'est Cobblemon qui remplit ces valeurs. Notre entité locale n'a pas de serveur : **c'est à nous de
les écrire**, sinon le renderer dessine le modèle de base, immobile.

| Donnée | Écrite par Phantasmon | Effet visuel |
|---|---|---|
| `PokemonEntity.ASPECTS` | Aspects de la forme + `shiny` + `male`/`female` | Bon modèle : Arceus Fée, Pikachu femelle, chromatique |
| `PokemonEntity.MOVING` | Vrai quand le Ghost se déplace | Animation de marche |
| `PokemonEntity.POSE_TYPE` | `WALK`, `STAND`, `FLY`, `HOVER` | Pose et animation |
| `BEAM_MODE` / `PHASING_TARGET_ID` | 1 = sortie, 3 = rappel ; dresseur visé | Faisceau de Poké Ball |

```java
entity.getEntityData().set(PokemonEntity.Companion.getMOVING(), ghost.moving);
entity.getEntityData().set(PokemonEntity.Companion.getPOSE_TYPE(), pose);
```

Les **aspects** sont la façon dont Cobblemon choisit un modèle : une forme n'est pas sélectionnée par son nom
(`fairy`) mais par ses aspects (`fairy-plate`). Écrire le nom de forme ne suffisait pas (chapitre 14).

## 8.4 Animations de sortie et de rappel

Cobblemon dessine le lancer de Poké Ball et le faisceau **côté client**, à partir de `BEAM_MODE`, et les fait
évoluer avec un minuteur côté serveur. Phantasmon reproduit ce minuteur avec les durées exactes de Cobblemon :

```java
if (owner != null) {
    owner.swing(InteractionHand.MAIN_HAND);                       // le dresseur fait le geste
    entity.setPhasingTargetId(owner.getId());                     // la balle part de lui
    playSound(clientLevel, owner.position(), CobblemonSounds.POKE_BALL_THROW);
}
entity.setBeamMode(BEAM_SEND_OUT);                                // faisceau de sortie
clientLevel.addEntity(entity);
later(THROW_DURATION, () -> entity.setPhasingTargetId(-1));       // 0,5 s
later(SEND_OUT_DURATION, () -> {                                  // 1,5 s
    entity.setBeamMode(BEAM_NONE);
    CobblemonPackets.dispatchLocally(new PlayPosableAnimationPacket(entity.getId(), Set.of("cry"), List.of()));  // cri
});
```

Astuce réutilisable : pour déclencher un effet de Cobblemon (le cri, l'anneau chromatique), on **fabrique le
paquet que le serveur aurait envoyé** et on le donne au gestionnaire client de Cobblemon
(`CobblemonPackets.dispatchLocally`, chapitre 12).

## 8.5 Le mouvement

L'entité n'ayant pas d'IA, `GhostEntityManager.tick()` la déplace à chaque tick :

1. **Cible** : un point derrière et à droite du propriétaire, calculé sur son **cap de déplacement** (pas sur la
   caméra : tourner la tête ne bouge pas le Ghost). Position du propriétaire : son entité `Player` locale si elle est
   chargée, sinon la dernière position reçue du backend.
2. **Pas** : vitesse proportionnelle à la distance (0,06 à 0,40 bloc par tick), avec une hystérésis (démarre à 1,3
   bloc, s'arrête à 0,4) pour éviter de trembler.
3. **Physique** : `entity.move(MoverType.SELF, vecteur)` gère les collisions avec les blocs ; gravité et saut d'un
   bloc sont calculés à la main.
4. **Rattrapage** : téléportation au-delà de 20 blocs ou si bloqué environ 3 s.
5. **Balade** : après 5 s d'immobilité du propriétaire, points aléatoires dans un rayon de 10 blocs.

```java
entity.noPhysics = false;                                  // collisions avec les blocs pendant NOTRE déplacement…
try {
    entity.move(MoverType.SELF, new Vec3(stepX, stepY, stepZ));
} finally {
    entity.noPhysics = true;                               // …puis plus aucune poussée entre entités
}
```

`noPhysics` a deux effets : pas de collision avec les blocs, et pas de poussée entre entités. On le désactive le
temps de notre propre `move` (pour garder les blocs), puis on le réactive : un gros Ghost (Arceus) ne pousse plus
son propriétaire. Le `finally` garantit qu'une erreur ne laisse pas l'entité dans le mauvais état.

## 8.6 Disparition

`despawn` joue le faisceau de rappel vers le propriétaire s'il est chargé chez ce client, sinon retire l'entité
immédiatement (`clientLevel.removeEntity(id, Entity.RemovalReason.DISCARDED)`). À la déconnexion, `despawnAll()` retire tout : une entité
locale oubliée resterait affichée.

## À retenir

- Une entité normale est créée par le serveur ; un Ghost est créé par chaque client, directement dans son
  `ClientLevel`.
- Réutiliser la classe d'entité d'un mod existant donne son rendu ; mais il faut alors remplir soi-même les données
  synchronisées que le serveur aurait fournies (aspects, mouvement, pose, faisceau).
- Une entité sans IA se déplace à la main à chaque tick ; `noPhysics` contrôle collisions et poussées.
