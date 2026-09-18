# Phase 7 — Robustesse, interface et performances

Validée le 18 septembre 2026.

## Limite HomeCore identifiée et corrigée

HomeCore 1.0 remplaçait une page de 16 appareils par une autre. Cela empêchait un Dashboard de consulter 100 appareils simultanément et de recevoir tous les événements du réseau. L'API publique HomeCore a donc évolué vers **1.1.0**, protocole **2**, avec `HomeCoreClient.subscribeNetwork`. Dashboard continue de dépendre exclusivement de `fr.lkdm.homecore.api.*`.

Le nouvel abonnement conserve jusqu'à 128 appareils et signale explicitement un dépassement. Les snapshots initiaux sont étalés (16/tick) ; les Metrics changées sont transmises par deltas (256/tick maximum), avec répartition équitable. Ajouts et suppressions mettent à jour la liste sans renvoyer les snapshots inchangés. Les événements couvrent tout le réseau autorisé. Une défaillance d'un provider est isolée dans un placeholder ERROR ; les autres appareils restent disponibles.

## Interface et sauvegarde

- Boutons sobres, navigation ajustée à la largeur et aux libellés traduits.
- SELECT réellement déroulant, sélection souris/clavier, sliders décimaux compatibles avec la validation HomeCore.
- Filtrage/tri recalculés sur changement de structure, pas sur chaque delta ; cache de présentations limité à 1024 entrées et vidé à la fermeture.
- SETTINGS sauvegarde la limite d'alertes et l'intervalle des mises à jour visuelles dans la configuration client NeoForge.
- Les textes trop longs sont tronqués sans perdre les Metrics/Actions valides. Les identifiants et budgets réseau restent stricts.
- Les références de widgets devenues indisponibles n'empêchent plus l'édition du layout. Les nouveaux identifiants restent vérifiés côté serveur.
- Accès rapide aux favoris depuis une fiche d'appareil ; barre de défilement HOME ; nettoyage des listeners à la fermeture.

## Vérifications réellement exécutées

- HomeCore : `gradlew.bat build test`, **85 tests JUnit réussis**, archive 1.1.0 vérifiée.
- Dashboard : **32 GameTests réussis**. Le test de redémarrage est exécuté effectivement dans les deux passes dédiées ci-dessous.
- `runPerformanceSmoke` : client Minecraft réel, **100 appareils / 800 Metrics**, 100 snapshots initiaux, un delta pour la Metric modifiée, aucune requête par caractère de recherche, filtres/capabilities, appareil Offline, ajout/suppression, événement de l'appareil 100, fenêtres réelles 640×480 et 1280×720, réglage sauvegardé, trois réouvertures et anciens listeners inertes.
- Régressions en jeu : `runActionSmoke`, `runConnectionSmoke`, `runAlertsSmoke`, `runPreferencesSmoke`, `runClientSmoke`. SELECT est ouvert et sélectionné par de vrais clics dans le contrôle.
- `runPersistence -PpersistencePass=write`, puis `runPersistence -PpersistencePass=read` dans **deux JVM serveur distinctes** : même réseau, deux blocs, propriétaire, état actif, widget, Metric référencée, géométrie et favori relus avec succès.
- Dernière commande : `gradlew.bat build runPersistence -PpersistencePass=read runPerformanceSmoke` → **BUILD SUCCESSFUL**.

Les essais ont révélé et corrigé un rejet des sliders extrêmes, une distribution inéquitable des deltas sous charge continue, le rejet des noms longs et une tâche Gradle incompatible avec le cache de configuration. Les lancements Minecraft sont maintenant sérialisés même avec Gradle parallèle.

Preuves : `build/phase7-final-validation.log`, `build/phase7-persistence-write.log`, `build/phase7-performance-retry.log`, `build/phase7-full-validation.log` (régressions réussies, tâche performance initialement échouée), captures dans `build/validation/{performance,actions,preferences,alerts}/screenshots/`.

## Fichiers créés/modifiés

Dashboard, sous `src/main/java/fr/lkdm/homelink/dashboard/` :

- Créés : `config/DashboardConfig.java`, `client/widget/SettingsView.java`, `client/widget/DashboardButton.java`, `client/widget/ActionDropdown.java`.
- Modifiés : `HomeLinkDashboard.java`, `client/screen/DashboardScreen.java`, `client/state/{DashboardClientState,DashboardPreferencesClient,DeviceActionView,DebugDeviceView}.java`, `client/rendering/MetricRendererRegistry.java`, `client/widget/{DeviceExplorerView,ActionControlRegistry,ActionPanel,HomeDashboardView}.java`, `network/PreferencesPayloads.java`, `server/DashboardPreferencesSavedData.java`.
- Supprimé : ancien `client/widget/DebugDeviceList.java`.
- Vérification : `ActionControlGameTests.java`, `NetworkWatchStateGameTests.java`, `PerformanceClientSmoke.java`, `PersistenceGameTests.java`, et mises à jour de `PreferenceGameTests`, `ActionStateGameTests`, `ConnectionClientSmoke`, `ActionClientSmoke`, `AlertsClientSmoke`.
- Gradle, métadonnées HomeCore requises et traductions EN/FR.

HomeCore adjacent : API de transport/client, cache client, synchronisation serveur, encodage de snapshots, version d'API, métadonnées/version Gradle, contrôles d'archive, tests `NetworkWatchTest`, `PayloadCodecTest`, `ClientDeviceCacheTest`, tests d'encodage et documentation du protocole.

## Limites explicites

128 fiches simultanées maximum ; au-delà, une alerte de limite apparaît. Les événements restent bornés et ne constituent pas un journal exhaustif durable. Un type de valeur non transportable par HomeCore reçoit un fallback ; Dashboard n'importe pas les classes du mod source. Les tests prouvent le fonctionnement à 100 appareils/800 Metrics, sans prétendre mesurer un FPS universel. HomeCore 1.1.0 doit être installé des deux côtés.
