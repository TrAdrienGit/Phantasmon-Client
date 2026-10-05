# Échanges côté client

> Classes : `trade/LiveTradeController`, `trade/LiveTradeState`, `gui/PhantasmonTradeScreen` (échange en direct) ;
> `trade/TradeCommandHandler`, `trade/TradeClient` (échange asynchrone). Protocole :
> `Phantasmon-Backend/Documentation/reference/websocket-protocol.md` §4. Vérifié le 2026-10-03.

## 1. Échange en direct

### Lancer

| Moyen | Détail |
|---|---|
| Roue Cobblemon (**R** sur un joueur) | Entrée « Échange Ghost » (Est, cyan) |

L'invité reçoit un message avec **[Accepter]** / **[Refuser]** cliquables (`/phantasmon trade join` /
`/phantasmon trade decline`). L'invitation expire après 60 s. L'écran s'ouvre chez les deux joueurs à
l'acceptation, au tick suivant (piège de fermeture du chat). Le premier Pokémon de l'équipe est proposé par défaut.

### Dans l'écran

- Rail **gauche** cliquable : choisir son offre. Rail **droit** en lecture seule : la fiche de droite montre
  toujours l'offre actuelle du partenaire.
- **⇄ ÉCHANGER** → « PRÊT ✓ » ; recliquer retire l'accord. Tout changement d'offre, d'un côté ou de l'autre, remet
  les deux joueurs à « non prêt » (règle du backend). Un « PRÊT ✓ » vert s'affiche à côté du nom du partenaire prêt.
- Les deux prêts : le backend exécute, la fenêtre « ÉCHANGE EN COURS » s'ouvre (Poké Ball, 2 allers-retours),
  passe à « ÉCHANGE TERMINÉ », puis l'écran se ferme seul ~1,8 s plus tard.
- **QUITTER** ou Échap : confirmation « QUITTER L'ÉCHANGE ? » ; confirmer annule pour les deux.
- Le Pokémon reçu prend **l'emplacement d'équipe** du Pokémon donné. Un Ghost échangé est rappelé.
- La ligne d'état du pied de page affiche l'attente, l'état du partenaire ou l'erreur traduite.

### Conception

- `LiveTradeState` est un miroir **pur** (sans Minecraft, testé par `LiveTradeStateTest`) de ce que le serveur a
  dit : il n'est jamais modifié par une action locale, seulement par `TradeSessionStarted`, `TradeSessionUpdate`,
  `TradeSessionCompleted`, `TradeSessionCancelled`.
- `LiveTradeController` traduit les actions du joueur en messages WebSocket et les réponses du serveur en état.
  Il passe par le WebSocket de `GhostSession` (pas de seconde connexion) ; les messages arrivent sur le thread
  client (`LiveTradeListener`).
- Seuls les Pokémon de l'équipe active sont échangeables.

### Écarts assumés avec la maquette

« PRÊT ✓ » du partenaire, ligne d'état du pied de page, textes des pieds de rail adaptés au rail droit non
cliquable, Poké Ball centrée sur la bande de transfert, menu réduit à 80 %, valeurs de la fiche agrandies,
types sous chaque attaque, couleurs +/- de la nature.

## 2. Échange asynchrone

Retiré du client (TODO-22, Adrien 2026-10-05 : aucun échange par commande). L'API REST (`/trades`) et les
notifications WebSocket (`TradeProposed`, `TradeAccepted`, `TradeCancelled`) existent toujours côté backend ; le client
affiche encore les notifications, sans commande pour y répondre.
