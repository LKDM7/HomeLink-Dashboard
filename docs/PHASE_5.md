# Phase 5 — HOME, favoris et widgets

Validée le 18 septembre 2026 avec Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21.

## Réalisation

HOME affiche le réseau, les états des appareils reçus, les favoris et les widgets génériques. Deux widgets sont disponibles : résumé d'appareil et Metric. Le mode édition permet de choisir l'appareil/la Metric, ajouter, déplacer par boutons, redimensionner et supprimer un widget. La grille logique comprend 12 colonnes ; le serveur refuse les chevauchements et les positions hors limites.

Les favoris et layouts appartiennent **au joueur et au réseau**. Ils sont sauvegardés dans les SavedData du monde. Aucun snapshot d'appareil n'est dupliqué dans cette sauvegarde. VIEW autorise les favoris personnels ; CONFIGURE est nécessaire pour modifier le layout. Les paquets vérifient le joueur réel, son menu, le point d'accès, le réseau, les permissions, les appareils référencés et la fréquence des demandes.

## Vérifications exécutées

- `gradlew.bat build runGameTestServer runClientSmoke` : BUILD SUCCESSFUL, **22 GameTests réussis**. Les quatre nouveaux tests couvrent la persistance, les données corrompues, la géométrie, les codecs bornés, les permissions et la limitation des requêtes.
- `gradlew.bat runPreferencesSmoke` : BUILD SUCCESSFUL. Client Minecraft réel : favori, deux types de widgets, déplacement, redimensionnement, fermeture/réouverture, sérialisation serveur et refus d'une modification de layout par un Viewer.
- Le test client de fondation crée maintenant le HomeNetwork par le menu du Server et associe réellement le Display à ce réseau.
- Captures inspectées : `build/validation/preferences/screenshots/homelink-home-smoke.png` et `homelink-editor-smoke.png`.
- Journaux : `build/phase5-client-command.log`, `build/phase5-validation-command.log`.

## Fichiers créés/modifiés

- `dashboard/widget/DashboardWidget.java`, `dashboard/layout/DashboardProfile.java`.
- `server/DashboardPreferencesSavedData.java`, `network/PreferencesPayloads.java`.
- `client/state/DashboardPreferencesClient.java`, `client/widget/HomeDashboardView.java`.
- `HomeLinkDashboard.java`, `client/screen/DashboardScreen.java`.
- Traductions `en_us.json`, `fr_fr.json` et `build.gradle`.
- Vérification : `PreferenceGameTests.java`, `PreferencesClientSmoke.java`, `DashboardClientSmoke.java`, `ConnectionClientSmoke.java`.

Les chemins Java de production sont relatifs à `src/main/java/fr/lkdm/homelink/dashboard/`, ceux de vérification à `src/verification/java/fr/lkdm/homelink/dashboard/verification/`.

## Limites à ce stade

HomeCore synchronise une page de 16 appareils : les compteurs détaillés et les widgets montrent uniquement les appareils actuellement reçus. La phase 7 doit résoudre cette limite. Une référence vers un appareil absent reste sauvegardée et affiche un état indisponible. La sauvegarde d'un widget Metric exige actuellement une Metric enregistrée. Limites explicites : 32 widgets et 64 favoris par profil, 64 réseaux par joueur, 4096 profils par monde. Le test de cette phase couvre le round-trip NBT et la réouverture ; le redémarrage complet fera partie de la validation finale.
