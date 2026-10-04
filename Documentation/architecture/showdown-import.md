# Import / export Showdown et identifiants Cobblemon

> Paquet `pokemon/showdown` : `ShowdownParser`, `ShowdownImportMapper`, `CobblemonIdentifiers`, `ShowdownPokemon`,
> `ShowdownExporter`, `ShowdownNames`, `CobblemonShowdownNames`.
> Tests : `ShowdownParserTest`, `ShowdownImportMapperTest`, `CobblemonIdentifiersTest`, `ShowdownExporterTest`.
> Décisions : D-09, D-14.
> Vérifié le 2026-10-03.

## 1. Chaîne de traitement

```text
Presse-papiers (texte Showdown, un ou plusieurs blocs séparés par une ligne vide)
  → ShowdownParser          texte → ShowdownPokemon (noms d'affichage bruts)
  → ShowdownImportMapper    noms → identifiants Cobblemon, valeurs par défaut Showdown → PokemonCreateRequestDto
  → POST /pokemon           un appel par Pokémon, première case libre du PC
```

Points d'entrée : bouton IMPORTER du PC, `/phantasmon pokemon import`, et bouton IMPORTER de l'éditeur (qui remplit
le formulaire sans rien enregistrer ni changer l'espèce). Le chat Minecraft ne gère pas le texte sur plusieurs
lignes : le presse-papiers est lu directement (`KeyboardHandler.getClipboard()`).

## 2. Format reconnu

```text
Bichou (Samurott-Hisui) (M) @ Assault Vest
Ability: Torrent
Level: 100
Shiny: Yes
Tera Type: Grass
Happiness: 255
EVs: 144 Atk / 64 Def / 136 SpD
IVs: 0 Spe
Timid Nature
- Avalanche
- Aqua Tail
- Body Slam
- Dark Pulse
```

| Élément | Règle |
|---|---|
| Première ligne | `Surnom (Espèce)` ou `Espèce`, sexe `(M)` / `(F)` facultatif, objet après `@` |
| `Ability:` | **Obligatoire** (sinon erreur `missing_ability`) |
| `Level:` | 100 par défaut |
| Nature | Ligne `<Nature> Nature` ; `Hardy` par défaut |
| `IVs:` | Stats non citées = **31** |
| `EVs:` | Stats non citées = **0** |
| `Shiny:`, `Tera Type:`, `Happiness:` | Facultatifs |
| `- Attaque` | Jusqu'à 4 |

## 3. Conversion des identifiants (`CobblemonIdentifiers`)

Règles vérifiées sur les fichiers de données du dépôt de référence `cobblemon` (et identiques à `toID()` de
Pokémon Showdown pour les espèces et attaques) :

| Catégorie | Règle | Exemples |
|---|---|---|
| Espèce, forme, attaque | Minuscules, tout caractère non alphanumérique supprimé (`slugConcat`) | `Mime Jr.` → `mimejr`, `Body Slam` → `bodyslam` |
| Espèce et forme | Coupure au **dernier** tiret : espèce + forme | `Samurott-Hisui` → `samurott` + `hisui` |
| Exceptions (tiret dans le nom) | Pas de coupure | `Ho-Oh`, `Porygon-Z`, `Jangmo-o`, `Hakamo-o`, `Kommo-o`, `Type-Null`, `Nidoran-M`, `Nidoran-F` |
| Talent, objet, nature, type Téra | Minuscules, mots séparés par `_` (`slugUnderscore`) | `Flash Fire` → `flash_fire`, `Assault Vest` → `assault_vest` |

## 4. Données produites

| Champ | Valeur |
|---|---|
| `species`, `form` | Identifiants ci-dessus |
| `level`, `nature`, `ability`, `is_shiny` | Colonnes |
| `cobblemon_data_version` | `"1.8.1"` (constante `PokemonCommandHandler.COBBLEMON_DATA_VERSION`) |
| `data.ivs`, `data.evs` | Les 6 stats, complétées par les valeurs par défaut |
| `data.moves` | Identifiants d'attaque |
| `data.nickname`, `data.gender` (`M`/`F`), `data.held_item`, `data.tera_type`, `data.friendship` | Seulement si présents dans le texte |

Les identifiants ne sont **pas** vérifiés contre les données de Cobblemon à l'import : une espèce inconnue sera
créée, puis signalée à l'affichage (modèle absent, `WARN` dans le log). Le backend ne vérifie que les IV/EV.

## 5. Export (CAD Partie 1 §11)

Côté client uniquement, comme l'import (D-09) : le client a déjà les Pokémon et les données Cobblemon.

| Point d'entrée | Ce qui est copié dans le presse-papiers |
|---|---|
| Bouton EXPORTER du PC (à droite d'IMPORTER) | L'équipe active, dans l'ordre des emplacements |
| Bouton EXPORTER de l'éditeur | Le Pokémon **tel qu'affiché**, modifications non enregistrées comprises |
| `/phantasmon pokemon export` | L'équipe active |
| `/phantasmon pokemon export <uuid>` | Un de ses Pokémon (PC ou équipe) |

- `ShowdownExporter` écrit les lignes dans l'ordre de l'export de Showdown et omet ses valeurs implicites : `Level`
  à 100, `Happiness` à 255, EV à 0, IV à 31. Plusieurs Pokémon sont séparés par une ligne vide.
- **Noms anglais** (`CobblemonShowdownNames`) : Showdown ne comprend que l'anglais, donc les traductions `en_us` de
  Cobblemon sont chargées exprès, quelle que soit la langue du jeu (`ClientLanguage.loadFrom`) :
  `cobblemon.species.<id>.name`, `cobblemon.move.<id>`, `cobblemon.ability.<id sans _>`, `item.cobblemon.<id>` puis
  `item.minecraft.<id>`, `cobblemon.nature.<id>`, `cobblemon.type.<id>`. Suffixe de forme : nom de la forme dans les
  données de l'espèce (`Samurott-Hisui`). Sans traduction : identifiant mis en majuscules (`flash_fire` →
  `Flash Fire`), que Showdown relit quand même (il normalise tous les noms).
- **Capacité cachée** : écrite `Hidden Power [Fire]` (type en anglais, calculé depuis les IV).
- **Une seule Puissance Cachée dans Cobblemon** : les 16 variantes de Showdown (`Hidden Power Fire`…) ont toutes
  l'identifiant `hiddenpower` (`realMove`), si bien que l'entrée `hiddenpower` du registre de Cobblemon garde le type
  de la dernière, **Eau**. En combat le type vient bien des IV (Showdown) ; à l'affichage (fiche du PC, éditeur), le
  badge est calculé depuis les IV (`PhantasmonCanvasScreen.moveType`). Les identifiants `hiddenpower<type>` n'existent
  pas dans Cobblemon : l'import les ramène à `hiddenpower`, et la migration V10 a corrigé les anciens Pokémon.
  À l'import, `Hidden Power [Type]` redevient l'identifiant Cobblemon unique `hiddenpower` (le type vient des IV).
- **Aller-retour garanti par test** (`exportReimportsToTheSamePokemon`) : espèce, forme, niveau, nature, talent,
  chromatique, surnom, sexe, objet, Tera, bonheur, EV, IV, capacités.

## 6. Données Cobblemon utiles à connaître

- Objets de combat : tag `#cobblemon:held/is_held_item` (inclut les Gemmes de type et les Graines de terrain).
  Cobblemon 1.8.1 n'a ni Méga-Gemmes, ni Cristaux Z, ni Energy Booster.
- Puissance Cachée : jamais stockée, calculée depuis les IV (`HiddenPowerCalculator`, formule Gen 2+ :
  IV tous à 31 → Ténèbres, tous à 0 → Combat).
- Natures : table standard des 25 natures (`NatureModifiers`) ; les 5 neutres n'affichent pas de +/-.
- Sexe : `data.gender`, sinon imposé par le ratio de l'espèce ou de la forme (`PokemonGender`) ; jamais deviné pour
  une espèce mixte.
- Export Showdown : **non implémenté**.
