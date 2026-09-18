# Phase 8 — Release 1.0.0

Release construite et vérifiée le 18 septembre 2026 pour **Minecraft 1.21.1, NeoForge 21.1.250, Java 21**. Dépendance obligatoire : **HomeCore 1.1.0** sur le client et le serveur.

## Livraison

- `build/libs/homelink_dashboard-1.0.0.jar` — 193660 octets (terminal industriel, français et modèles révisés).
- `../HomeCore/build/libs/homecore-1.1.0.jar` — 160 513 octets.
- Distribution avec les deux JAR, licence, guides et empreintes : `build/release/HomeLink-Dashboard-1.0.0.zip`.

| Archive | SHA-256 |
| --- | --- |
| Dashboard | `7acbc378f626084ce4dc434fd32a82bb4e562ea2942e7798549d3977017a3d11` |
| HomeCore | `c426a8222faf528187ab44fbcd37d5ee67410ac016036614e5f8f2b14bce9fa7` |

## Vérification finale

Commande exécutée : `gradlew.bat build test runGameTestServer runPreferencesSmoke`.

Résultat réel : **BUILD SUCCESSFUL**. Le contrôle d'archive valide la version, la dépendance HomeCore, la cible Minecraft, la licence embarquée, les recettes, modèles, loot tables et **82 clés de traduction EN/FR cohérentes**. Les fichiers JSON de l'archive sont analysés. Le JAR ne contient ni classes HomeCore, ni code de vérification, ni ancien package CoreLink.

**34 GameTests requis réussis**, dont deux nouveaux tests utilisant les recettes et loot tables réellement chargées par Minecraft. `test` n'a pas de sources JUnit dans Dashboard ; les tests fonctionnels Dashboard sont les GameTests et scénarios client. Les **85 tests JUnit HomeCore** ont été exécutés séparément pendant la phase 7.

Le client Minecraft lancé avec `lang:fr_fr` a réussi le scénario HOME : favoris, widgets, déplacement, redimensionnement, réouverture, isolation par réseau et refus du Viewer. La capture française a été inspectée. Les scénarios complets à 100 appareils/800 Metrics, SELECT par clics, événements, association des blocs et redémarrage serveur sont consignés dans [PHASE_7](PHASE_7.md).

Journal final : `build/phase8-release-validation.log`. Captures françaises : `build/validation/preferences/screenshots/`. `git diff --check` est passé ; seuls des avertissements de conversion LF/CRLF ont été émis.

## Recettes et ressources

Révision visuelle du HomeLink Server : boîtier sombre, trois baies avec poignées, aérations latérales et panneau de contrôle. Géométrie statique partagée par les variantes Online/Offline/Error. `gradlew.bat build runClientSmoke` a réussi après modification ; les orientations/états, interactions et rendu en jeu ont été vérifiés. Journal : `build/server-model-validation.log` ; capture : `build/validation/client/screenshots/homelink-server-model.png`. Les empreintes et le pack de livraison ci-dessus ont été actualisés.

- HomeLink Server : 5 lingots de fer, 1 quartz, 2 redstones et 1 comparateur.
- HomeLink Dashboard : 5 lingots de fer, 3 blocs de verre et 1 redstone.
- Les recettes sont déblocables via des advancements ; les deux blocs utilisent une pioche et rendent leur item via leurs loot tables.
- Les modèles sont des modèles Minecraft simples, sans rendu permanent de la GUI sur les blocs. Les 24 combinaisons d'orientation/état ont été vérifiées en jeu pendant la phase de fondation.

## Fichiers de cette phase

- Créés : `README.md`, `LICENSE`, `docs/USER_GUIDE.md`, `docs/DEVELOPER_GUIDE.md`, `docs/RELEASE_AUDIT.md`, ce rapport.
- Créés : `src/main/resources/data/homelink_dashboard/recipe/{home_server,dashboard_display}.json` et `advancement/recipes/redstone/{home_server,dashboard_display}.json`.
- Créé : `src/verification/java/fr/lkdm/homelink/dashboard/verification/RecipeGameTests.java`.
- Modifiés : `gradle.properties` (version 1.0.0 et bornes de compatibilité), `src/main/templates/META-INF/neoforge.mods.toml`, `build.gradle` (licence et contrôle de l'archive).
- Produits : JAR final, dossier de distribution, ZIP et `SHA256SUMS.txt` dans `build/release/`.

## Limites de la V1

128 fiches d'appareils synchronisées simultanément ; recherche et compteurs de statut limités à ces fiches. Les alertes restent bornées et temporaires. Une alerte dont la source se trouve hors de cette limite ne peut pas ouvrir une fiche non reçue. Les widgets/favoris sont personnels par joueur/réseau. Pas de tablette, caméra, multibloc géant, automatisation avancée ou intégration directe de mods tiers.

Les essais en jeu utilisent les runs NeoForge du workspace ; une installation séparée dans un launcher externe n'est pas revendiquée comme testée. Les instructions d'installation sont dans le [guide utilisateur](USER_GUIDE.md).

## Révision NAS sur deux blocs

Le serveur utilise maintenant deux moitiés et une seule BlockEntity, avec interaction depuis les deux moitiés, vérification de la hauteur libre et un seul objet récupéré. Quatre textures animées locales donnent les voyants vert, orange, bleu et rouge ; hors ligne les voyants sont fixes. Aucun paquet supplémentaire n'est envoyé pour les animations.

Validation : build + runGameTestServer + runClientSmoke réussis, 38 GameTests et interaction réseau réelle depuis la moitié haute. Les 36 variantes de modèles et les huit images de chacune des quatre animations sont chargées dans Minecraft. Journal : build/nas-validation-final.log. Capture : build/validation/client/screenshots/homelink-server-model.png.

Fichiers : HomeServerBlock.java ; modèles, blockstate, loot table et textures du serveur ; TallServerGameTests.java et DashboardClientSmoke.java ; guide utilisateur et rapport. Les serveurs de l'ancien modèle doivent être reposés après redémarrage et réassociés à leur réseau existant.

## Révision écran mural et GUI

L'écran possède un cadre graphite, un support, une façade en relief et une représentation décorative qui suit Online/Offline/Error. La collision correspond aux quatre orientations. La GUI dispose d'un thème partagé, d'onglets actifs et focus visibles, d'un format adaptable jusqu'à 520 × 340 unités GUI, d'un résumé HOME compact ou à quatre compteurs, de fiches d'appareil hiérarchisées et de panneaux réseau cohérents. Les traductions EN/FR possèdent 86 clés concordantes.

Validation réelle : build + 38 GameTests + six scénarios client réussis dans build/gui-redesign-validation.log. Après les derniers ajustements d'espacement, les scénarios blocs, favoris, alertes et performance ont été rejoués avec succès dans build/gui-redesign-final.log (BUILD SUCCESSFUL). Le dernier scénario vérifie 100 appareils, 800 Metrics et les tailles GUI 320 × 240 et 640 × 360. Les captures HOME française, écran mural, fiche appareil et réseau ont été inspectées.

Captures : build/validation/client/screenshots/homelink-display-model.png ; build/validation/preferences/screenshots/homelink-home-large.png ; build/validation/performance/screenshots/homelink-100-small.png et homelink-100-large.png.

Fichiers concernés : DashboardDisplayBlock ; DashboardTheme (nouveau) ; DashboardScreen ; DashboardButton ; HomeDashboardView ; DeviceExplorerView ; MetricRendererRegistry ; NetworkView ; SettingsView ; modèles dashboard_display ; traductions ; DashboardClientSmoke et PreferencesClientSmoke ; USER_GUIDE et DESIGN.md. Les données et permissions HomeCore ne changent pas. Redémarrer le client pour charger les nouvelles classes ; les Displays déjà placés adoptent le nouveau modèle.

## Traduction française complétée

136 clés EN/FR concordantes. Les états, rôles, filtres, booléens, types de widgets et résultats sont localisés à l'affichage par DashboardText. Les identifiants HomeCore et les contrôles de permissions restent inchangés. Les textes français ont été simplifiés et les séquences Unicode doublement échappées corrigées. Les codes inconnus et contenus fournis par les autres mods conservent leur texte de secours.

Validation : build et 38 GameTests réussis ; quatre scénarios client réussis dans build/french-validation.log. HOME a vérifié explicitement les traductions françaises, accents et codes inconnus (HOMELINK_FRENCH_LABELS_OK). Les actions et alertes ont ensuite été rejouées en français avec build runActionSmoke runAlertsSmoke -PsmokeLanguage=fr_fr : BUILD SUCCESSFUL dans build/french-locale-validation.log. Les captures HOME, actions et réseau françaises ont été inspectées. git diff --check est passé.

Fichiers : DashboardText ; MetricRendererRegistry ; DashboardScreen ; HomeDashboardView ; DeviceExplorerView ; NetworkView ; AlertCenterView ; ActionPanel ; ActionControlRegistry ; DashboardMenu ; fr_fr.json et en_us.json ; PreferencesClientSmoke ; build.gradle (langue de vérification explicite) ; guides utilisateur et développeur.

## Direction terminal industriel

Après le choix explicite de l'utilisateur : gris neutres, cadre biseauté, boutons physiques avec onglet actif enfoncé, voyants discrets, panneaux encastrés et libellés en casse normale. Les compteurs HOME sont réunis sur une ligne ; les favoris passent à 48 unités GUI et les appareils à 44. Les positions enregistrées des widgets restent intactes. Référence visuelle : terminaux Applied Energistics 2, lien dans DESIGN.md ; aucune ressource tierce copiée.

Validation : build runPreferencesSmoke runPerformanceSmoke -PsmokeLanguage=fr_fr réussi dans build/industrial-gui-validation.log, avec favoris/layout et 100 appareils/800 Metrics aux deux tailles de GUI. Le scénario des huit actions a réussi dans build/industrial-gui-final.log. Le nouveau test par clic de la liste a ensuite révélé que AbstractContainerScreen absorbait les clics avant les vues personnalisées : DashboardScreen traite désormais leurs zones avant la classe parente. Le correctif a passé build runConnectionSmoke -PsmokeLanguage=fr_fr dans build/industrial-gui-click-validation.log, avec HOMELINK_COMPACT_LIST_HITBOXES_OK et BUILD SUCCESSFUL. Recherche, filtres, deltas, suppression et révocation restent validés. git diff --check est passé.

Fichiers : DashboardTheme, DashboardButton, DashboardScreen, HomeDashboardView, DeviceExplorerView, harmonisation des couleurs dans les autres vues et MetricRendererRegistry, langues EN/FR, ConnectionClientSmoke, DESIGN.md et USER_GUIDE.md. Captures : build/validation/preferences/screenshots/homelink-home-smoke.png et homelink-home-large.png ; build/validation/connection/screenshots/phase3-device-details.png. Redémarrer le client pour charger le nouveau rendu Java.
