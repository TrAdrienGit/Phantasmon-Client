# 10. Les Mixins

## 10.1 Le problème

Parfois, aucune API ne permet de faire ce qu'on veut :

- l'interface de combat de Cobblemon envoie le choix du joueur **au serveur Minecraft** ; pour un combat Ghost, il
  faut l'envoyer **ailleurs** ;
- la roue d'interaction de Cobblemon (touche R) est construite à partir d'une liste **fermée** d'options ; on veut
  y ajouter les nôtres.

Il faudrait **modifier le code** de Minecraft ou de Cobblemon. On ne peut pas recompiler ces programmes, mais on
peut modifier leur **bytecode** (le code compilé que la JVM exécute) au moment où il est chargé. C'est ce que fait
la bibliothèque **Mixin** (de SpongePowered), intégrée à Fabric.

## 10.2 Le principe

Un **Mixin** est une classe spéciale qui décrit une modification d'une classe **cible** :

```java
@Mixin(ClasseCible.class)                      // quelle classe modifier
public abstract class MonMixin {
    @Inject(method = "methodeCible", at = @At("HEAD"))   // où insérer du code
    private void phantasmon$monAjout(…, CallbackInfo ci) {
        // ce code est copié DANS methodeCible, au début
    }
}
```

Au démarrage, quand la JVM charge `ClasseCible`, Mixin réécrit son bytecode en y insérant notre code. Notre classe
`MonMixin` n'existe jamais en tant qu'objet : c'est un « patron » de modification.

Bonnes pratiques visibles dans le projet :

- préfixer les méthodes ajoutées (`phantasmon$…`) pour éviter tout conflit de nom avec d'autres mods ;
- garder le Mixin **minuscule** : il intercepte et **délègue** à une classe normale, testable et lisible ;
- n'agir que dans notre cas précis et laisser tout le reste inchangé.

## 10.3 Déclarer les Mixins

```json
// src/client/resources/phantasmon.client.mixins.json
{
  "required": true,
  "package": "com.mystaria.phantasmon.client.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "ClientCommonPacketListenerImplMixin",
    "DistributionUtilsMixin",
    "ActionEffectTimelineMixin",
    "ActionEffectInstructionsMixin",
    "MoveInstructionMixin",
    "InteractWheelGuiAccessor",
    "InteractWheelGuiFactoryMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

`defaultRequire: 1` : chaque injection **doit** trouver sa cible au moins une fois, sinon le jeu refuse de démarrer
avec une erreur explicite. C'est volontaire : après une mise à jour de Cobblemon, mieux vaut un plantage clair au
lancement qu'un comportement faux en silence. Le fichier est référencé dans `fabric.mod.json`.

## 10.4 `@Inject` : insérer du code

### Au début, et annuler (`HEAD` + `cancellable`)

```java
// mixin/ClientCommonPacketListenerImplMixin.java
@Mixin(ClientCommonPacketListenerImpl.class)                         // classe vanilla qui envoie les paquets du client
public abstract class ClientCommonPacketListenerImplMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void phantasmon$routeGhostBattleChoices(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundCustomPayloadPacket custom
                && custom.payload() instanceof BattleSelectActionsPacket choice
                && GhostBattles.intercepts(choice)) {               // seulement un choix de combat Ghost
            GhostBattles.onLocalChoice(choice);                      // l'envoyer à notre moteur
            ci.cancel();                                             // et NE PAS l'envoyer au serveur
        }
    }
}
```

- La méthode injectée reçoit **les mêmes paramètres** que la méthode cible, plus un `CallbackInfo`.
- `ci.cancel()` interrompt la méthode cible (possible seulement avec `cancellable = true`).
- Tous les autres paquets passent normalement.

### Au début, avec une valeur de retour (`CallbackInfoReturnable`)

```java
// mixin/DistributionUtilsMixin.java
@Mixin(value = DistributionUtilsKt.class, remap = false)            // fonctions Kotlin de fichier de Cobblemon
public abstract class DistributionUtilsMixin {
    @Inject(method = "runOnServer", at = @At("HEAD"), cancellable = true)
    private static <T> void phantasmon$runOnBattleThread(Function0<? extends T> block,
                                                         CallbackInfoReturnable<CompletableFuture<T>> cir) {
        if (!BattleThread.isOwnThread()) return;                     // pas notre thread : comportement normal
        CompletableFuture<T> future = new CompletableFuture<>();
        BattleThread.get().executeOnOwnThread(() -> {
            try { future.complete(block.invoke()); } catch (Throwable ex) { future.completeExceptionally(ex); }
        });
        cir.setReturnValue(future);                                  // remplace le résultat et annule la suite
    }
}
```

Pour une méthode qui renvoie une valeur, `CallbackInfoReturnable<T>.setReturnValue(...)` fournit le résultat à la
place de la méthode d'origine.

### À la fin (`RETURN`)

```java
// mixin/InteractWheelGuiFactoryMixin.java
@Inject(method = "createPlayerInteractGui", at = @At("RETURN"))
private static void phantasmon$addGhostOptions(PlayerInteractOptionsPacket optionsPacket,
                                               CallbackInfoReturnable<InteractWheelGUI> cir) {
    GhostWheelOptions.addTo(((InteractWheelGuiAccessor) (Object) cir.getReturnValue()).phantasmon$getOptions(), optionsPacket);
}
```

À `RETURN`, `cir.getReturnValue()` donne l'objet que la méthode s'apprête à renvoyer : ici la roue terminée, à
laquelle on ajoute deux options.

## 10.5 `@Accessor` : lire un champ privé

```java
// mixin/InteractWheelGuiAccessor.java
@Mixin(value = InteractWheelGUI.class, remap = false)
public interface InteractWheelGuiAccessor {
    @Accessor("options")
    Multimap<Orientation, InteractWheelOption> phantasmon$getOptions();
}
```

Mixin fait « implémenter » cette interface par la classe cible, avec un getter vers son champ privé `options`. On
l'utilise par un double transtypage : `((InteractWheelGuiAccessor) (Object) roue).phantasmon$getOptions()` (le
passage par `Object` évite que le compilateur Java refuse un transtypage qu'il croit impossible).

## 10.6 `@Redirect` : remplacer une instruction précise

```java
// mixin/MoveInstructionMixin.java
@Mixin(value = MoveInstruction.class, remap = false)
public abstract class MoveInstructionMixin {
    @Redirect(method = "invoke$lambda$1",
              at = @At(value = "FIELD",
                       target = "Lcom/cobblemon/mod/common/battles/interpreter/instructions/MoveInstruction;future:Ljava/util/concurrent/CompletableFuture;",
                       opcode = Opcodes.PUTFIELD))
    private static void phantasmon$storeThroughSetter(MoveInstruction instruction, CompletableFuture<Unit> future) {
        instruction.setFuture(future);
    }
}
```

`@Redirect` remplace **une instruction** de la méthode cible par un appel à notre méthode. Ici, l'instruction
`PUTFIELD future` (écrire directement dans le champ `future`) devient un appel au setter `setFuture`, sur lequel un
autre Mixin est accroché. La cible est décrite en **notation bytecode** : `Lchemin/de/Classe;nomDuChamp:Ltype;`.

`invoke$lambda$1` est le nom d'une lambda **générée par le compilateur Kotlin** : il peut changer à la moindre
mise à jour de Cobblemon. C'est le Mixin le plus fragile du projet (d'où `defaultRequire`).

## 10.7 `remap`

Les noms de Minecraft dans le jar du joueur sont différents de ceux du code source (mappings, chapitre 3) : par
défaut, Mixin les **traduit** (`remap = true`). Cobblemon n'est pas obfusqué : ses noms sont les mêmes partout, et
il faut `remap = false`, sinon Mixin cherche une traduction qui n'existe pas.

## 10.8 Trouver où injecter

On ne devine pas une cible de Mixin : on lit le code réel (chapitre 11).

1. Trouver la classe et la méthode responsables du comportement (décompiler le jar).
2. Vérifier la signature exacte (paramètres, type de retour, `static` ou non).
3. Pour `@Redirect` / `@At("FIELD"|"INVOKE")`, lire le bytecode (`javap -c -p`) pour voir l'instruction exacte.
4. Lancer le jeu : avec `defaultRequire`, une erreur immédiate signale une cible introuvable.

## 10.9 Quand ne pas utiliser de Mixin

Un Mixin crée une dépendance forte au **détail interne** d'un autre programme. Avant d'en écrire un : existe-t-il
un événement Fabric, une API publique, une autre façon d'obtenir le résultat ? Le projet a d'abord livré une touche
(G, B) pour inviter un joueur, et n'a ajouté les entrées dans la roue Cobblemon (par Mixin) qu'ensuite, en
connaissance de cause. La liste complète des Mixins et la procédure de vérification après une mise à jour sont dans
`../architecture/mixins.md`.

## À retenir

- Un Mixin modifie le bytecode d'une classe au chargement : dernier recours quand aucune API ne suffit.
- `@Inject` (HEAD / RETURN, `cancel`, `setReturnValue`), `@Redirect` (une instruction), `@Accessor` (un champ privé).
- Mixins minces qui délèguent, préfixe de nom, `remap = false` pour un mod non obfusqué, `defaultRequire` pour
  échouer bruyamment.
- Toujours lire le code réel de la cible avant d'écrire l'injection.
