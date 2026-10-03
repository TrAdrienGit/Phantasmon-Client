# Compiler le mod

## 1. Prérequis

| Outil | Version | Remarque |
|---|---|---|
| JDK | **25** | Exigé par Fabric Loom 1.18 pour **exécuter** Gradle. Le bytecode du mod reste Java 21. Ne pas « corriger » la CI vers le JDK 21. |
| Git | récent | |
| Python 3 + Pillow | facultatif | Uniquement pour régénérer les textures de l'écran d'échange |

Sans JDK 25, Gradle échoue dès la configuration :

```text
Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
```

Installer un JDK 25 sous Windows :

```powershell
winget install -e --id Microsoft.OpenJDK.25
```

Inutile de changer le `JAVA_HOME` du système : le préciser pour la commande suffit (§2).

## 2. Compiler

```bash
./gradlew build
```

Avec un JDK par défaut différent (exemple, adapter le chemin) :

```bash
JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot" ./gradlew build
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
.\gradlew.bat build
```

Résultat : `build/libs/phantasmon-client-<version>.jar` (actuellement `1.0.0`). Ignorer le
`-sources.jar`. Les dépendances (Minecraft, Fabric API, Cobblemon via le Maven Modrinth) sont téléchargées au
premier build.

## 3. Lancer un Minecraft de développement

```bash
./gradlew runClient
```

Lance Minecraft 1.21.1 avec Fabric, Fabric API, Cobblemon et le mod. Pour se connecter au backend, il faut un vrai
compte Microsoft (la session de développement par défaut est hors-ligne et sera refusée).

## 4. Tests unitaires

```bash
./gradlew test
```

Logique pure uniquement (40 tests au 2026-10-03). Détail et recette manuelle :
[`testing-and-qa.md`](testing-and-qa.md).

## 5. Régénérer les textures de l'écran d'échange

```bash
python scripts/generate_trade_textures.py
```

Écrit dans `src/main/resources/assets/phantasmon/textures/gui/trade/`. À relancer après toute modification de
couleur dans le script (valeurs reprises du CSS de la maquette).

## 6. Intégration continue

`.github/workflows/build.yml` (chaque push et pull request) : validation du wrapper Gradle, JDK **25** Microsoft,
`./gradlew build` (compilation et tests), jars publiés en artefact.

## 7. Mettre à jour une dépendance

| Dépendance | Où | Attention |
|---|---|---|
| Cobblemon | `cobblemon_version` dans `gradle.properties` | Utiliser l'**identifiant de version Modrinth** du build **Fabric** (les builds Fabric et NeoForge partagent le numéro `1.8.1`). Revalider tous les Mixins : [`architecture/mixins.md`](../architecture/mixins.md). |
| Minecraft / Fabric API / Loader | `gradle.properties`, `fabric.mod.json` | Plancher de Loader volontairement bas (0.18.1) pour la compatibilité avec les modpacks |
| Kotlin stdlib (`compileOnly`) | `build.gradle` | Aligner sur la version embarquée par Cobblemon |
