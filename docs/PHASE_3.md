# Phase 3 — Device Explorer

Explorateur fonctionnel : recherche locale nom/type/capabilities, filtre par statut,
tri alphabétique, sélection, détails/position et Metrics typées. Liste et Metrics
défilantes avec rendu limité à la zone visible. Les données sont reconstruites aux
changements d'état et non à chaque frame.

MetricRendererRegistry interprète les types publics HomeCore : booléens, entiers,
nombres avec unité (dont température), pourcentages, énergie, position. Un type,
une valeur ou un renderer inconnu/défectueux conserve un affichage de secours.

Validation : build réussi ; **15 GameTests réussis** (dont 4 tests déterministes du
cycle de vie client et 3 tests de présentation des Metrics) ; test client réel réussi
avec recherche, filtre, fiche puis deltas/statut/suppression/révocation.
Capture contrôlée : `build/validation/connection/screenshots/phase3-device-details.png`.
Journaux : `build/phase3-server-command.log`, `build/phase3-client-command.log`.

Fichiers créés :

- `client/widget/DeviceExplorerView.java`
- `client/rendering/MetricRendererRegistry.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/MetricRendererGameTests.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/ClientStateGameTests.java`
- `docs/PHASE_3.md`

Les chemins `client/` sont relatifs à `src/main/java/fr/lkdm/homelink/dashboard/`.
Fichiers modifiés : `client/state/DebugDeviceView.java`, `DashboardClientState.java`,
`client/screen/DashboardScreen.java`, `ConnectionClientSmoke.java`, traductions EN/FR.

Limite à ce stade : la recherche porte sur la page de données synchronisées,
suivant la limite HomeCore décrite en phase 2. Les Actions restent pour la phase 4.
