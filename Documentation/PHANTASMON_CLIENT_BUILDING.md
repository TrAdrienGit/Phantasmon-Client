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

## 4. Tester les fonctionnalités actuelles

### 4.1 Heartbeat backend (Phase 0 groundwork)

**Désactivé par défaut** (Adrien : 2026-09-26, pour ne pas spammer le chat par défaut). Taper
`/phantasmon toggle-ping` pour l'activer/désactiver pour la session de jeu en cours (pas persisté). Une
fois activé, le mod envoie un appel `GET /health` au backend **toutes les 30 secondes** et affiche le
résultat dans le chat du joueur (préfixe `[Phantasmon]`). Le ping s'arrête aussi automatiquement à la
déconnexion.

Pour le voir fonctionner :

1. Démarrer le backend en local sur le port 8080 (voir `PHANTASMON_BACKEND_RUNNING.md` dans le repo
   backend).
2. Lancer Minecraft avec le mod installé et rejoindre un monde (solo ou serveur).
3. Taper `/phantasmon toggle-ping` → message de confirmation d'activation.
4. Observer le chat : un message `[Phantasmon] Backend 200 {"status":"UP",...}` doit apparaître toutes
   les 30 secondes. Si le backend n'est pas joignable, le message affiche `Backend injoignable (...)` à
   la place — c'est le comportement attendu, pas un bug.

### 4.2 Connexion (Phase 5)

Taper `/phantasmon login` dans le chat une fois connecté à un monde. Le mod, dans l'ordre :

1. Appelle `GET /version` (avant toute authentification, CAD Partie 3 §E).
2. Si la version du mod est sous `min_supported_version` : affiche un message d'incompatibilité et
   s'arrête là (pas de tentative d'auth).
3. Si compatible mais sous `current_version` : affiche un avertissement non bloquant et continue.
4. Effectue le flux Mojang `joinServer` (nécessite un **vrai compte Microsoft/Mojang** — les comptes
   hors-ligne/crackés sont explicitement non supportés et rejetés avant même l'appel réseau) puis
   `POST /auth/session`.
5. En cas de succès, le JWT (access + refresh) est conservé **en mémoire uniquement** le temps de la
   session de jeu (jamais écrit sur disque) et un message de confirmation s'affiche.

Tous les messages (succès, erreurs, avertissements) passent par `lang/fr_fr.json`/`lang/en_us.json` — le
jeu doit être en français ou en anglais pour voir la traduction correspondante, aucun texte brut n'est
affiché.

**Notes** :
- L'URL du backend est actuellement codée en dur sur `http://localhost:8080`
  (`BackendConfig.BASE_URL`) — le client et le backend doivent tourner sur la même machine pour ce test.
  Ce sera rendu configurable avant toute utilisation réelle multi-machines.
- Pour tester le cas "version incompatible", changer temporairement
  `phantasmon.version.min-supported` côté backend (`application.properties`) à une valeur supérieure à
  `1.0.0` (version actuelle du mod, `gradle.properties`), relancer le backend, puis réessayer
  `/phantasmon login`. Le message affiche un lien cliquable (actuellement un placeholder
  `https://modrinth.com/mod/phantasmon`, à remplacer une fois le mod réellement publié).
- Pour tester le renouvellement automatique du token (`SessionRefreshScheduler`, vérifie toutes les 60s
  si un renouvellement est nécessaire), baisser temporairement `phantasmon.jwt.access-ttl` côté backend
  à une valeur courte mais **pas trop courte** (ex. `PT3M` — avec un TTL de 1 min pile, la marge de
  sécurité de 30s combinée à l'intervalle de vérification de 60s peut faire manquer la fenêtre). Rester
  connecté plus longtemps que le TTL choisi et vérifier dans les logs backend qu'un `POST /auth/refresh`
  apparaît tout seul, sans relancer `/phantasmon login`. Remettre le TTL par défaut ensuite.

---

## 5. Dépannage courant

| Symptôme | Cause probable |
|---|---|
| `Dependency requires at least JVM runtime version 25` | JDK utilisé pour Gradle < 25 — voir §1.1 |
| Le mod n'apparaît pas dans le jeu | Mauvais dossier `mods/`, Fabric API manquante/incompatible, version Minecraft ≠ 1.21.1 |
| Toujours `Backend injoignable` dans le chat | Backend non lancé, mauvais port, pare-feu local |
| Crash au lancement mentionnant un mixin | Ne devrait pas arriver (mixins actuellement vides) — signaler si observé |
| `/phantasmon login` répond "comptes hors-ligne non supportés" | Compte de lancement en mode hors-ligne/cracké (`User.Type.LEGACY`) — utiliser un vrai compte Microsoft |
| `/phantasmon login` échoue à la vérification Mojang | Jeu lancé hors mode premium, ou API Mojang temporairement indisponible |
