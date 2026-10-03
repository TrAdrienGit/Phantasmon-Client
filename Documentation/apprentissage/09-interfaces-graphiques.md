# 9. Les interfaces graphiques

## 9.1 Les écrans

Un menu Minecraft est un **`Screen`**. Un seul écran est actif à la fois : `Minecraft.getInstance().setScreen(new
MonEcran())` l'ouvre, `setScreen(null)` revient au jeu. Méthodes à redéfinir :

| Méthode | Appelée |
|---|---|
| `init()` | À l'ouverture et à chaque redimensionnement de la fenêtre : calculer la mise en page |
| `render(GuiGraphics, mouseX, mouseY, partialTick)` | À chaque image : tout redessiner |
| `mouseClicked`, `mouseDragged`, `mouseReleased`, `mouseScrolled` | Événements souris |
| `keyPressed`, `charTyped` | Clavier (touches spéciales / caractères) |
| `onClose()` | Fermeture (Échap) |
| `isPauseScreen()` | Vrai = met le jeu en pause en solo (Phantasmon renvoie faux) |

Le rendu est en **mode immédiat** : à chaque image on redessine tout à partir de l'état ; il n'y a pas d'objets
graphiques persistants. L'écran garde son état dans ses champs (Pokémon sélectionné, boîte affichée…).

Coordonnées : unités « GUI », origine en haut à gauche, mises à l'échelle selon l'option *Taille de l'interface* du
joueur. `this.width` / `this.height` sont la taille de l'écran dans ces unités.

## 9.2 Dessiner : `GuiGraphics` et `PoseStack`

| Appel | Dessine |
|---|---|
| `graphics.fill(x1, y1, x2, y2, couleurARGB)` | Rectangle plein (`0xAARRGGBB`) |
| `graphics.drawString(font, texte, x, y, couleur, ombre)` | Texte |
| `graphics.blit(texture, x, y, …)` | Image |
| `graphics.blitSprite(sprite, x, y, w, h)` | Sprite de l'atlas, étiré en « nine-slice » si son `.mcmeta` le demande (bords nets, centre étiré) |
| `graphics.renderItem(stack, x, y)` | Icône d'objet |
| `graphics.enableScissor(...)` / `disableScissor()` | Découpe : rien ne se dessine hors de la zone |

Le **`PoseStack`** (`graphics.pose()`) est la pile de transformations appliquées à tout ce qu'on dessine ensuite :
`translate`, `scale`, `mulPose` (rotation). `pushPose()` sauvegarde l'état, `popPose()` le restaure. Toujours par
paires : un `push` sans `pop` décale tout le reste de l'image.

## 9.3 Un canevas à taille fixe

Les maquettes du projet sont dessinées sur une page de **1600 × 900 px**. Plutôt que de recalculer chaque
coordonnée selon la fenêtre, le socle commun `PhantasmonCanvasScreen` applique **une seule** mise à l'échelle :

```java
// gui/PhantasmonCanvasScreen.java (raccourci)
protected void init() {
    float s = Math.min(1f, Math.min(this.width / 800f, this.height / 450f));
    scale = s / 2f * MENU_SCALE;                               // MENU_SCALE = 0.8 : 80 % de la fenêtre
    originX = (this.width - CANVAS_W * scale) / 2f;            // centré
    originY = (this.height - CANVAS_H * scale) / 2f;
}

protected void beginCanvas(GuiGraphics g) {
    PoseStack pose = g.pose();
    pose.pushPose();
    pose.translate(originX, originY, 0);
    pose.scale(scale, scale, 1f);                              // tout ce qui suit est en « pixels de maquette »
}

protected void endCanvas(GuiGraphics g) { g.pose().popPose(); }

protected double toCanvasX(double guiX) { return (guiX - originX) / scale; }   // souris → maquette
```

Les sous-classes écrivent directement les cotes de la maquette (`CARD_X = 219`…). La souris suit le chemin inverse
(`toCanvasX`/`toCanvasY`) avant tout test de clic.

```java
// gui/PhantasmonPcScreen.java (raccourci)
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderWorldBackdrop(graphics, mouseX, mouseY, partialTick);   // le jeu flouté autour
    beginCanvas(graphics);
    renderRootPanel(graphics);
    renderHeader(graphics, mx, my);
    renderTeamRail(graphics, mx, my);
    renderPokemonCard(graphics, selectedPokemon(), …);
    renderGridPanel(graphics, mx, my);
    renderFooter(graphics, mx, my);
    renderDraggedModel(graphics);                                 // le Pokémon tenu suit la souris
    endCanvas(graphics);
    if (deleteConfirmOpen) renderDeleteModal(graphics, mouseX, mouseY);
}

public boolean mouseClicked(double mouseX, double mouseY, int button) {
    double x = toCanvasX(mouseX), y = toCanvasY(mouseY);
    if (inside(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) { onClose(); return true; }
    if (inside(x, y, IMPORT_X, IMPORT_Y, IMPORT_W, IMPORT_H)) { importFromClipboard(); return true; }
    …
}
```

Le projet n'utilise **aucun widget** de Minecraft (boutons, champs de saisie) : ils ne s'affichent pas correctement
dans un canevas mis à l'échelle. Boutons, champs et listes déroulantes sont dessinés et gérés par l'écran lui-même
(tests de zone `inside`, saisie par `charTyped`/`keyPressed`).

## 9.4 Règles de rendu apprises à la dure

| Règle | Pourquoi |
|---|---|
| `flush()` avant chaque `blit` | En 1.21.1, `fill` et `drawString` sont mis en lot et dessinés plus tard, alors que `blit` dessine immédiatement : une texture dessinée **après** un rectangle dans le code finissait **dessous** à l'écran. Vider le lot avant garantit que l'ordre du code est l'ordre visuel. |
| Fenêtres modales à z = 2000 | Au-dessus des icônes d'objets et des modèles 3D, qui ont leur propre profondeur |
| Texte à échelle **entière** | La police pixel de Minecraft n'est nette qu'à ×1, ×2… |
| Traits d'au moins 1 pixel écran | Mis à l'échelle, un trait de 1 px de maquette peut tomber entre deux pixels et disparaître |
| Pas de gras pour un texte aligné à droite | La police du modpack dessine le gras plus large que ce que mesure `font.width()` |
| Textures avec dégradés générées en PNG | Minecraft ne sait pas dessiner de dégradé diagonal, de halo ni de flou : `scripts/generate_trade_textures.py` génère ces images depuis les valeurs CSS de la maquette |

## 9.5 Dessiner un modèle 3D de Pokémon

Cobblemon possède une fonction qui dessine un Pokémon animé dans une interface, `drawProfilePokemon`. Elle est
écrite en Kotlin avec 9 paramètres à valeur par défaut. Depuis Java, le raccourci qui permet d'omettre ces
paramètres (`drawProfilePokemon$default`) est **invisible** pour le compilateur Java : on l'appelle par réflexion.

```java
// gui/PokemonGuiRendering.java (raccourci)
PoseStack poseStack = graphics.pose();
poseStack.pushPose();
try {
    poseStack.translate(centerX, anchorY, 0);
    poseStack.scale(outerScale, outerScale, 1f);                         // la taille se règle ICI
    Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13f, 35f, 0f));
    drawProfilePokemonDefault().invoke(null,                             // méthode trouvée par réflexion, mise en cache
            renderable, poseStack, rotation, null, new FloatingState(), 0f, 4.5f /* échelle interne de Cobblemon */,
            null, false, 0f, 0f, 0f, 0f, 0f, 0f, 0, 65416 /* masque des paramètres par défaut */, null);
} catch (Exception ex) {
    LOG.warn("Failed to render Pokémon model for {}", speciesId, ex);   // une icône ratée…
} finally {
    poseStack.popPose();                                                // …ne doit jamais casser tout l'écran
}
```

Les valeurs (rotation 13°/35°, échelle interne 4.5, masque `65416`) ont été relevées en **décompilant l'écran PC de
Cobblemon** (chapitre 11), plutôt que devinées. Le `finally` garantit l'équilibre `push`/`pop` même en cas
d'erreur.

## 9.6 Données et réseau dans un écran

L'écran PC charge **toute** la liste des Pokémon du joueur (480 au maximum), filtre en mémoire pour la boîte et
l'équipe, et **recharge tout** après chaque action (déplacement, suppression, import). C'est simple et toujours
juste : l'écran n'essaie jamais de deviner le résultat d'une action, il affiche ce que le serveur dit.

Un glisser-déposer n'envoie que la **destination** (`PATCH /pokemon/{uuid}` avec `team_slot` ou `box_id` +
`box_slot`) ; le backend s'occupe de déplacer ou d'échanger.

## À retenir

- Un `Screen` redessine tout à chaque image depuis son état ; `PoseStack` transforme ce qui est dessiné, toujours
  par paires `push`/`pop`.
- Un canevas à échelle unique permet d'écrire les cotes de la maquette telles quelles ; la souris est convertie dans
  l'autre sens.
- Connaître l'ordre réel de rendu de la version utilisée (lots, flush, profondeur).
- Afficher l'état du serveur plutôt que de prédire le résultat d'une action.
