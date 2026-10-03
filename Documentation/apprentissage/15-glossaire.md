# 15. Glossaire

Les termes réseau, base de données et sécurité (REST, JSON, JWT, WebSocket, transaction…) sont dans le glossaire
du parcours backend (`Phantasmon-Backend/Documentation/apprentissage/16-glossaire.md`).

| Terme | Définition | Chapitre |
|---|---|---|
| **Acteur de combat** (`BattleActor`) | Un camp dans un combat Cobblemon ; `GhostBattleActor` pour Phantasmon | 12 |
| **Aspect** | Étiquette Cobblemon qui choisit la variante visuelle d'un Pokémon (forme, chromatique, sexe) | 8 |
| **Brigadier** | Bibliothèque de Mojang qui analyse les commandes sous forme d'arbre | 6 |
| **Bytecode** | Code compilé exécuté par la JVM ; ce que modifient les Mixins | 10 |
| **Canevas** | Espace de dessin à taille fixe (1600 × 900) mis à l'échelle d'un coup | 9 |
| **Client pur** | Client connecté à un serveur distant, sans serveur intégré | 1, 11 |
| **`ClientLevel`** | Le monde tel que le client le connaît ; on peut y ajouter des entités locales | 8 |
| **Codec** | Façon d'écrire un paquet en octets et de le relire | 12 |
| **`CompletableFuture`** | Résultat asynchrone sur lequel on enchaîne des étapes | 2 |
| **`Component`** | Texte Minecraft : contenu, style, actions de clic, traduction | 6 |
| **Décompiler** | Reconstituer un code lisible à partir d'un jar | 11 |
| **Données synchronisées** (`SynchedEntityData`) | Valeurs d'entité normalement envoyées par le serveur et lues par le renderer | 8 |
| **Entité** | Objet mobile du monde (joueur, Pokémon…) | 8 |
| **Entrypoint** | Classe que le loader appelle au démarrage (`ClientModInitializer`) | 4 |
| **Espace de noms** | Préfixe des ressources (`phantasmon:`, `cobblemon:`) | 4 |
| **Événement Fabric** | Point d'accroche où enregistrer une fonction (`JOIN`, `END_CLIENT_TICK`…) | 5 |
| **Fabric / Fabric API** | Loader de mods / mod de bibliothèque qui fournit les accroches courantes | 1 |
| **Fabric Loom** | Plugin Gradle qui prépare Minecraft pour le développement de mods | 3 |
| **`flush()`** | Dessiner immédiatement ce qui est en attente dans les lots de rendu | 9 |
| **GraalJS** | Moteur JavaScript utilisé par Cobblemon pour exécuter Showdown | 12 |
| **Gson** | Bibliothèque JSON côté client | 2 |
| **`GuiGraphics`** | Outil de dessin des interfaces (rectangles, texte, textures) | 9 |
| **Hôte (combat)** | Le client qui exécute le moteur de combat | 12 |
| **Intermediary** | Noms stables de Fabric utilisés dans le jar final | 3 |
| **`KeyMapping`** | Touche configurable par le joueur | 6 |
| **Kotlin** | Langage de la JVM dans lequel Cobblemon est écrit | 2, 11 |
| **Mappings** | Correspondance noms obfusqués → noms lisibles (officiels Mojang ici) | 3 |
| **Mixin** | Modification du bytecode d'une classe au chargement | 10 |
| **`modImplementation`** | Dépendance Gradle vers un mod (remappé par Loom) | 3 |
| **Nine-slice** | Étirement d'une texture qui garde ses bords nets | 9 |
| **Obfuscation** | Renommage illisible des classes du jeu distribué | 3 |
| **Paquet** | Message réseau entre client et serveur Minecraft | 1, 12 |
| **`partialTick`** | Fraction de tick écoulée, pour un rendu fluide | 5 |
| **`PoseStack`** | Pile de transformations (déplacement, échelle, rotation) appliquées au dessin | 9 |
| **Puits** (*sink*) | Fonction qui reçoit les paquets destinés à un acteur | 12 |
| **Réflexion** | Accéder à une classe et l'appeler par programme, à l'exécution | 2 |
| **`remap`** | Traduction des noms par Mixin ; `false` pour un mod non obfusqué comme Cobblemon | 10 |
| **Renderer** | Code qui dessine une entité à partir de son état | 8 |
| **`ResourceLocation`** | Identifiant `espace:chemin` d'une ressource | 4 |
| **`Screen`** | Un écran/menu de Minecraft | 9 |
| **Serveur intégré** | Serveur qui tourne dans le client en solo ou en hôte LAN | 1 |
| **Showdown** | Moteur de combat Pokémon (JavaScript) utilisé par Cobblemon | 12 |
| **Tag** | Liste nommée d'objets ou de blocs définie par les données | 11 |
| **Thread client** | Le thread unique où vit le jeu ; ne jamais le bloquer | 2, 5 |
| **Tick** | Pas de simulation, 20 par seconde | 5 |
| **Timeline d'action** (*action effect*) | Description JSON d'une animation d'attaque Cobblemon | 12 |
| **Yarn** | Mappings de la communauté Fabric (autres noms que Mojang) | 3 |
