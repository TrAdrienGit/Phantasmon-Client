# Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Dependency requires at least JVM runtime version 25` | Gradle lancé avec un JDK < 25 | Utiliser un JDK 25 ([`building.md`](building.md)) |
| Le mod n'apparaît pas en jeu | Mauvais dossier `mods/`, Fabric API absente, Minecraft ≠ 1.21.1 | Vérifier l'installation ([`installing.md`](installing.md)) |
| Crash au démarrage mentionnant Cobblemon ou Kotlin | Cobblemon absent ou dans une autre version | Cobblemon **1.8.1** Fabric |
| Crash au démarrage avec une erreur Mixin | Une cible a changé après une mise à jour de Cobblemon ou Minecraft | Voir [`architecture/mixins.md`](../architecture/mixins.md) §3 et joindre le log |
| Pas de connexion automatique, aucun message | Backend injoignable (comportement voulu : silence) | Démarrer le backend, puis `/phantasmon login` |
| « comptes hors-ligne non supportés » | Jeu lancé avec un compte hors-ligne | Utiliser un compte Microsoft |
| Échec de la vérification Mojang | Jeu lancé hors mode premium, ou API Mojang indisponible | Réessayer plus tard |
| `Backend injoignable` (ping activé) | Backend arrêté, mauvaise adresse, pare-feu | Vérifier `backend_url` dans `config/phantasmon.json` (et l'avertissement éventuel `Invalid 'backend_url'` dans le log), puis le backend |
| `UUID non valide à la position N` | UUID tapé incomplet | Utiliser l'UUID complet (cliquer dessus dans le chat l'insère) |
| « Pas encore connecté au service de présence Ghost » | WebSocket pas encore ouvert, ou perdu (backend redémarré) | Le client se reconnecte seul (message « Reconnecté au backend ») ; `/phantasmon login` relance l'essai tout de suite. Si la session a expiré pendant la coupure, `/phantasmon login` refait la connexion complète |
| Déconnexion de tous les joueurs d'un coup, log backend coupé net sans message d'arrêt, pics de lag | Le backend a perdu son disque ou son processus a été tué (2026-10-04 : disque D: externe en USB remonté par Windows, événements `disk` 51 / `Ntfs` 50, 140, 98) | Journal Système de Windows ; garder le backend sur un disque interne (TODO-17) |
| Le Ghost d'un autre joueur n'apparaît pas | Dimension différente, autre backend, empreinte différente (test LAN), ou espèce inconnue (`Cannot render Ghost: unresolved species` dans le log) | Même dimension, même backend, `/phantasmon admin debug fingerprint` en test LAN (admin) |
| « Ce joueur n'est pas connecté à Phantasmon » (échange ou combat) | L'autre client n'a pas de session WebSocket avec **ce** backend | Il doit être connecté (`/phantasmon login`) au même backend |
| L'écran d'échange ne s'ouvre pas après [Accepter] | Invitation expirée (60 s) ou inviteur déjà occupé | Relancer l'invitation |
| Le combat ne démarre pas chez l'invité | Moteur Showdown non démarré chez l'hôte (« Le moteur de combat n'a pas pu démarrer ») | Consulter le log de l'hôte (`Cannot`, `engine`) |
| Animations d'attaque absentes | Action effects non chargées ou non reliées (`was not claimed by any instruction`) | Joindre le log de l'hôte ; vérifier les Mixins après une mise à jour de Cobblemon |
| La touche du PC ne fait rien | Écran ouvert puis refermé par le chat (régression du différé au tick suivant) | Vérifier que l'ouverture passe par un drapeau consommé au tick |
