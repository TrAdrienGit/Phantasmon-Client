# 3. Gradle, Loom et les mappings

Le chapitre 4 du parcours backend explique Gradle (dépendances, portées, tâches, wrapper). Ici, ce qui change pour
un mod.

## 3.1 Le problème de l'obfuscation

Le jeu distribué par Mojang est **obfusqué** : ses classes s'appellent `a`, `b`, `fzk`… pour réduire la taille et
gêner la rétro-ingénierie. Impossible d'écrire un mod contre `fzk.a(b)`. Les **mappings** sont des tables de
correspondance « nom obfusqué → nom lisible ».

| Mappings | Exemple de nom | Remarque |
|---|---|---|
| **Officiels Mojang** (*Mojmap*) | `Minecraft`, `Component`, `GuiGraphics`, `displayClientMessage` | Publiés par Mojang ; **utilisés par Phantasmon** |
| Yarn (communauté Fabric) | `MinecraftClient`, `Text`, `DrawContext`, `sendMessage` | Beaucoup de tutoriels Fabric les utilisent |
| Intermediary (Fabric) | `class_310`, `method_1551` | Noms stables entre versions, utilisés dans le jar final |

Conséquence pratique : un tutoriel écrit avec Yarn utilise d'autres noms. Il faut traduire, ou mieux, vérifier dans
le code réel du jeu (chapitre 11). Ce piège a été rencontré dès le premier jour du projet.

## 3.2 Fabric Loom

**Loom** est le plugin Gradle de Fabric. Il :

- télécharge Minecraft et le désobfusque avec les mappings choisis ;
- **remappe** les mods dont on dépend pour qu'ils utilisent les mêmes noms que notre code ;
- compile, puis remappe notre jar final vers les noms *intermediary* (pour qu'il fonctionne chez le joueur) ;
- fournit `runClient` pour lancer un Minecraft de développement avec le mod.

## 3.3 Lire `build.gradle`

```groovy
plugins {
    id 'net.fabricmc.fabric-loom-remap' version "${loom_version}"   // Loom (version dans gradle.properties)
    id 'maven-publish'
}

repositories {
    mavenCentral()                                                 // JUnit
    maven {                                                        // Cobblemon via le Maven de Modrinth
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
        content { includeGroup "maven.modrinth" }
    }
}

loom {
    splitEnvironmentSourceSets()                                   // sépare le code client du code commun (4.2)
    mods { "phantasmon" { sourceSet sourceSets.main; sourceSet sourceSets.client } }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings loom.officialMojangMappings()                         // noms officiels Mojang
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    modImplementation "maven.modrinth:cobblemon:${project.cobblemon_version}"
    compileOnly "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"         // compiler contre l'API Kotlin de Cobblemon
    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

sourceSets {                                                       // les tests voient le code du jeu de sources « client »
    test {
        compileClasspath += sourceSets.client.output
        runtimeClasspath += sourceSets.client.output
    }
}

tasks.withType(JavaCompile).configureEach { it.options.release = 21 }   // bytecode Java 21
```

- `modImplementation` (et non `implementation`) : la dépendance est **un mod** que Loom doit remapper.
- Les versions sont dans `gradle.properties` :

```properties
minecraft_version=1.21.1
loader_version=0.18.1
loom_version=1.18-SNAPSHOT
fabric_api_version=0.116.17+1.21.1
cobblemon_version=gBW3vLC7
version=1.0.0
```

### Piège : l'identifiant de version Cobblemon

Cobblemon publie sur Modrinth un build Fabric et un build NeoForge qui portent tous deux le numéro `1.8.1`. Le
Maven de Modrinth ne sait pas lequel donner pour `cobblemon:1.8.1`. On utilise donc l'**identifiant unique** de la
version Fabric (`gBW3vLC7`). Ne pas le « corriger » en `1.8.1`.

## 3.4 Deux versions de Java

Le mod est compilé en bytecode **Java 21** (celui que Minecraft 1.21.1 utilise pour tourner). Mais Loom 1.18
lui-même exige une JVM **25** pour exécuter Gradle. Il faut donc un JDK 25 pour construire, et le joueur n'a besoin
que de Java 21 pour jouer. Sans JDK 25 :

```text
Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
```

## 3.5 Les tâches utiles

```bash
./gradlew build        # compile, teste, produit build/libs/phantasmon-client-1.0.0.jar
./gradlew runClient    # lance Minecraft de développement avec le mod
./gradlew test         # tests unitaires
```

Le jar `-sources.jar` contient le code source, pas le mod.

## À retenir

- Minecraft est obfusqué ; les mappings donnent des noms lisibles ; ce projet utilise les mappings **officiels**.
- Loom désobfusque, remappe les mods dépendants, construit et lance le jeu de développement.
- `modImplementation` pour un mod ; versions centralisées dans `gradle.properties`.
- JDK 25 pour construire, Java 21 dans le jar.
