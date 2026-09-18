# Phase 6 — ALERTS et NETWORK

Le Dashboard écoute les `DeviceEventNotification` de l'API publique HomeCore. L'historique de session est borné (128 événements par défaut, maximum 512), avec date, gravité, source, type et données lisibles. Les filtres sont locaux. Le clic ouvre la fiche de la source encore disponible. Les données sont effacées à la fermeture et lors d'une révocation.

NETWORK présente les métadonnées réellement reçues : nom, propriétaire, nombre de membres, rôle du joueur, appareils et états. Une section avancée affiche l'UUID. Les états CONNECTED, UNREACHABLE et OFFLINE reflètent la session, sans simuler un système radio.

## Vérifications

`gradlew.bat build runGameTestServer runAlertsSmoke` : BUILD SUCCESSFUL, **25 GameTests réussis**. Le client Minecraft réel publie et reçoit INFO/WARNING/CRITICAL, filtre les alertes, ouvre l'appareil source, vérifie l'éviction à la limite de l'historique, supprime la source et révoque VIEW. Les métadonnées propriétaire/membres sont vérifiées.

L'inspection des captures a identifié une infobulle trop large ; elle a été corrigée avec retour à la ligne et limites de largeur/hauteur. La compilation et le test client sont réexécutés après cette correction.

Preuves : `build/phase6-validation-command.log`, `build/phase6-tooltip-validation.log`, captures dans `build/validation/alerts/screenshots/`.

## Fichiers

- Créés : `client/state/AlertView.java`, `client/widget/AlertCenterView.java`, `client/widget/NetworkView.java`.
- Modifiés : `client/state/DashboardClientState.java`, `client/screen/DashboardScreen.java`, `client/widget/HomeDashboardView.java`, traductions EN/FR, `build.gradle`.
- Vérification : `AlertStateGameTests.java`, `AlertsClientSmoke.java`.

Sources de production sous `src/main/java/fr/lkdm/homelink/dashboard/`, vérification sous `src/verification/java/fr/lkdm/homelink/dashboard/verification/`.

## Limites temporaires

HomeCore 1.0 ne livre que les événements des appareils de la page active (16 appareils). Une suppression invalide cette page et demande un refresh. L'abonnement réseau de la phase 7 doit lever ces limites. L'historique reste volontairement limité à la session, sans archivage sur plusieurs jours.
