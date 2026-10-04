# Traductions (i18n)

> Fichiers : `src/main/resources/assets/phantasmon/lang/fr_fr.json` et `en_us.json` (224 clés chacun, identiques au
> 2026-10-03). Exigence du CAD Partie 3 §L : français et anglais dès la V1, aucune chaîne en dur.

## 1. Règles

1. **Aucun texte visible en dur** dans le code : toujours `Component.translatable("phantasmon.…")`.
2. Toute clé ajoutée l'est **dans les deux fichiers** dans le même changement.
3. Les erreurs du backend arrivent sous forme de `error_code` et sont traduites par
   `network/BackendErrorMessages` (code → clé). Un code absent de la table affiche `phantasmon.error.unknown`.
   Ne jamais afficher le code brut ni le message d'une exception.
4. Les noms issus de Cobblemon (espèces, talents, natures, attaques, objets, types) utilisent les traductions de
   Cobblemon, pas des clés Phantasmon. Pièges : `AbilityTemplate.getDisplayName()` et `Nature.getDisplayName()`
   renvoient une **clé** (à envelopper dans `Component.translatable`), `MoveTemplate.getDisplayName()` renvoie un
   texte déjà traduit, `Species.getTranslatedName()` et `ItemStack.getHoverName()` aussi.
5. Les messages du chat commencent par `[Phantasmon]`.
6. Formats : `%s` pour les paramètres ; les symboles ⇄ ♂ ♀ ★ ✓ s'affichent via la police Unicode de secours de
   Minecraft.

## 2. Espaces de noms

| Préfixe | Nombre de clés | Usage |
|---|---|---|
| `phantasmon.trade.*` | 71 | Échange en direct (écran, invitations) et asynchrone, erreurs d'échange |
| `phantasmon.pc.*` | 72 | Écran PC et éditeur (libellés, boutons, aide, confirmations) |
| `phantasmon.battle.*` | 29 | Invitations, chrono, fin de combat, erreurs |
| `phantasmon.pokemon.*` | 24 | Commandes Pokémon, import, export, erreurs de légalité et de place |
| `phantasmon.auth.*` | 13 | Connexion, version, erreurs Mojang |
| `phantasmon.ghost.*` | 10 | Sortie/rappel, connexion WebSocket |
| `phantasmon.error.*` | 4 | Erreurs génériques (`not_authenticated`, `network`, `ownership_mismatch`, `unknown`) |
| `key.phantasmon.*`, `key.categories.phantasmon` | 5 | Touches et leur catégorie |
| `phantasmon.ping.*` | 2 | Ping de santé |
| `phantasmon.wheel.*` | 2 | Entrées de la roue Cobblemon |

## 3. Codes d'erreur traduits

La correspondance complète code → clé est dans le catalogue du backend :
`Phantasmon-Backend/Documentation/reference/error-codes.md`. Pour ajouter un code :

1. l'ajouter dans `BackendErrorMessages.KEYS` ;
2. ajouter la clé dans `fr_fr.json` et `en_us.json` ;
3. compléter la colonne « Clé de traduction client » du catalogue.

## 4. Vérifier

```bash
python -c "import json;a=json.load(open('src/main/resources/assets/phantasmon/lang/fr_fr.json',encoding='utf-8'));b=json.load(open('src/main/resources/assets/phantasmon/lang/en_us.json',encoding='utf-8'));print(sorted(set(a)^set(b)) or 'OK')"
```

En jeu : changer la langue de Minecraft et vérifier qu'aucun texte n'apparaît sous forme de clé brute.
