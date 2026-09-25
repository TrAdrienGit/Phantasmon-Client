# Phantasmon Client — Compiler et installer le mod

Ce document explique comment compiler le mod Phantasmon en `.jar` et l'installer dans une instance
Minecraft, indépendamment du backend (voir `Documentation/PHANTASMON_BACKEND_RUNNING.md` dans le repo
`Phantasmon-Backend` pour le lancer).

---

## 1. Prérequis

- **JDK 25** — obligatoire pour exécuter Gradle/Fabric Loom sur ce projet, **même si** le mod compile
  lui-même en bytecode Java 21 (`sourceCompatibility`/`targetCompatibility` dans `build.gradle`). C'est
  une exigence de l'outillage de build (Loom 1.18), pas du mod lui-même. Sans JDK 25, `./gradlew build`
  échoue à l'étape de configuration avec une erreur du type :
  ```
  Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM.
  ```
- Pour **jouer** avec le mod une fois compilé : Minecraft **1.21.1**, Fabric Loader **≥ 0.18.1** (plancher
  volontairement bas pour rester compatible avec les modpacks encore sur cette version — voir
  `fabric.mod.json`), Fabric API **0.116.17+1.21.1** (ou une version compatible plus récente, qui
  n'exige elle-même que Fabric Loader ≥ 0.15.11) — ceux-ci tournent normalement sous un Java 21
  classique (le runtime de jeu, différent du JDK utilisé pour builder).

### 1.1 Installer un JDK 25 (si absent)

```powershell
winget install -e --id Microsoft.OpenJDK.25
```

Si ce JDK n'est pas votre JDK système par défaut, il n'est pas nécessaire de changer `JAVA_HOME`
globalement : indiquez-le juste pour la commande de build (voir §2).

---

## 2. Compiler le mod

Depuis la racine du repo `Phantasmon-Client` :

```bash
./gradlew build
```

Si votre JDK par défaut n'est pas la version 25, précisez-le pour cette seule commande plutôt que de
changer votre configuration système (exemple avec un JDK Microsoft installé sous Windows, à adapter au
chemin réel) :

```bash
JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot" \
PATH="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot/bin:$PATH" \
./gradlew build
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

### Résultat

Le jar du mod est produit dans `build/libs/` :

```
build/libs/phantasmon-client-<version>.jar
```

⚠️ Ignorez `phantasmon-client-<version>-sources.jar` (jar des sources, pas le mod lui-même) — c'est
`phantasmon-client-<version>.jar` (sans suffixe) qu'il faut installer.

---

## 3. Installer le mod dans Minecraft

1. Installer **Fabric Loader** pour Minecraft 1.21.1 (via le launcher officiel Fabric, ou un launcher de
   modpack compatible comme Modrinth App/CurseForge).
2. Télécharger et placer **Fabric API** (version `0.116.17+1.21.1` ou compatible) dans le dossier
   `mods/` de l'instance — Phantasmon en dépend.
3. Copier `phantasmon-client-<version>.jar` dans ce même dossier `mods/`.
4. Lancer Minecraft avec le profil Fabric 1.21.1.

Phantasmon est un mod **client-only** : il ne nécessite rien côté serveur Minecraft, et peut être utilisé
en solo comme sur n'importe quel serveur vanilla/Fabric classique.

---

## 4. Tester la fonctionnalité actuelle

L'unique fonctionnalité présente à ce stade : une fois connecté à un monde/serveur, le mod envoie un
appel `GET /health` au backend **toutes les secondes** et affiche le résultat dans le chat du joueur
(préfixe `[Phantasmon]`). Le ping s'arrête automatiquement à la déconnexion.

Pour le voir fonctionner :

1. Démarrer le backend en local sur le port 8080 (voir `PHANTASMON_BACKEND_RUNNING.md` dans le repo
   backend).
2. Lancer Minecraft avec le mod installé et rejoindre un monde (solo ou serveur).
3. Observer le chat : un message `[Phantasmon] Backend 200 {"status":"UP",...}` doit apparaître chaque
   seconde. Si le backend n'est pas joignable, le message affiche `Backend injoignable (...)` à la place
   — c'est le comportement attendu, pas un bug.

**Note** : l'URL du backend est actuellement codée en dur sur `http://localhost:8080/health`
(`PhantasmonClient.HEALTH_URI`) — le client et le backend doivent tourner sur la même machine pour ce
test. Ce sera rendu configurable avant toute utilisation réelle multi-machines.

---

## 5. Dépannage courant

| Symptôme | Cause probable |
|---|---|
| `Dependency requires at least JVM runtime version 25` | JDK utilisé pour Gradle < 25 — voir §1.1 |
| Le mod n'apparaît pas dans le jeu | Mauvais dossier `mods/`, Fabric API manquante/incompatible, version Minecraft ≠ 1.21.1 |
| Toujours `Backend injoignable` dans le chat | Backend non lancé, mauvais port, pare-feu local |
| Crash au lancement mentionnant un mixin | Ne devrait pas arriver (mixins actuellement vides) — signaler si observé |
