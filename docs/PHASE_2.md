# Phase 2 — HomeCore connection

Implémentation et vérifications réalisées le 18 septembre 2026.

## Fonctionnement

Le serveur non associé propose de créer un véritable HomeNetwork HomeCore.
Le propriétaire d'un serveur ou écran non associé peut aussi sélectionner un réseau
pour lequel il possède VIEW et CONFIGURE. Les contrôles sont refaits côté serveur ;
les tentatives de mutation sont limitées par joueur. Les UUID restent dans les BlockEntities.
L'association remplace la session ouverte par une session attachée au réseau choisi.

À l'ouverture, DashboardClientState écoute le cache public HomeCore, demande la page
initiale, décode les snapshots et applique uniquement les Metrics modifiées. Le debug
affiche nom, type, état et Metrics avec compteurs snapshots/deltas. Pagination et
actualisation sont explicites, sans polling de liste. Fermer libère l'écoute et l'abonnement.
L'état physique est recalculé côté serveur toutes les 20 ticks, sans envoi de packet
si son état n'a pas changé.

## Vérifications

- Build réussi.
- 8 GameTests serveur réussis : fondation, NBT, création réelle, association,
  droits CONFIGURE/VIEW, refus de création depuis un écran, invalidation de session.
- `runConnectionSmoke` réussi sur vrai client : 17 appareils d'un type inconnu de
  Dashboard, page de 16 puis 1, delta 73→85 sans snapshot additionnel, statut WARNING,
  suppression, refresh puis révocation Viewer et nettoyage.
- Journaux : `build/phase2-server-command.log`, `build/phase2-client-command.log`.

## Limite HomeCore identifiée avant intégration

HomeCore 1.0.0 limite le transport à une seule page de 16 appareils en direct par joueur.
Dashboard respecte ce contrat. Il ne prétend pas actualiser simultanément les autres pages.
Une suppression invalide la page ; le bouton Actualiser recharge la liste autorisée.
Les métadonnées réseau sont un snapshot, les Metrics sont des deltas, les changements
de statut provoquent un snapshot du seul appareil concerné.
L'objectif de 100 appareils simultanément à jour nécessitera une évolution HomeCore.

## Fichiers

Créés :

- `src/main/java/fr/lkdm/homelink/dashboard/network/AccessPointSession.java`
- `src/main/java/fr/lkdm/homelink/dashboard/server/DashboardNetworks.java`
- `src/main/java/fr/lkdm/homelink/dashboard/client/state/DashboardClientState.java`
- `src/main/java/fr/lkdm/homelink/dashboard/client/state/DebugDeviceView.java`
- `src/main/java/fr/lkdm/homelink/dashboard/client/widget/DebugDeviceList.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/ConnectionClientSmoke.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/NetworkSetupGameTests.java`
- `docs/PHASE_2.md`

Modifiés : `build.gradle`, `DashboardMenu.java`, `DashboardAccess.java`,
`DashboardScreen.java`, `AccessPointBlock.java`, `AccessPointBlockEntity.java`,
`assets/homelink_dashboard/lang/en_us.json`, `fr_fr.json`.

Les phases suivantes sont autorisées à s'enchaîner par la dernière instruction utilisateur,
avec compilation et tests après chaque phase.
