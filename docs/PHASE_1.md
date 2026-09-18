# HomeLink Dashboard — phase 1

## Résultat

Fondation implémentée pour Minecraft 1.21.1, NeoForge 21.1.250 et Java 21.
Mod ID : `homelink_dashboard`. Version de développement : `1.0.0-SNAPSHOT`.
La version finale 1.0.0 reste réservée à la phase 8.

- Deux blocs plaçables : HomeLink Server et HomeLink Dashboard (panneau fin orientable).
- Deux BlockEntity types, avec une base commune de persistance : propriétaire, UUID HomeNetwork optionnel, état actif.
- Menu sans inventaire ouvert par clic droit, écran client sombre et bouton Fermer.
- Onglet créatif, modèles de blocs/items, drops, tag pioche, traductions EN/FR.
- États de blocs ONLINE/OFFLINE/ERROR, sans renderer animé ni copie des appareils.
- Contrôle serveur à l'ouverture et pendant la session : propriétaire du bloc non associé, sinon permission HomeCore VIEW ; distance, dimension, présence du bloc et état actif.
- Aucune classe HomeCore recopiée, aucun import interne ou intégration propre à un appareil.

## Dépendance réellement utilisée

`settings.gradle` inclut le projet adjacent `../HomeCore` comme composite Gradle et substitue
`fr.lkdm.homecore:homecore:1.0.0` par son artefact réel. Le wrapper HomeLink construit ainsi
HomeCore lorsque nécessaire. Un autre emplacement peut être choisi avec `-Phomecore_dir=C:/chemin/HomeCore`.
Aucun fichier du projet HomeCore n'a été modifié.

API inspectée : `DashboardAPI`, `HomeNetworkManager`, `HomeNetwork`, `PermissionValidator`,
`DeviceSchema`, `HomeCoreClient`. La phase 1 utilise uniquement `DashboardAPI.networks`,
`getNetwork` et `DashboardAPI.hasPermission(..., Permission.VIEW)`.

Les mutateurs des BlockEntities sont des opérations de confiance réservées au thread serveur.
Ils ne sont pas exposés par packet. Toute future commande/GUI d'association devra contrôler
le droit de configuration avant leur appel.

L'ouverture suit le mécanisme [Menu NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/gui/menus/)
avec `IMenuTypeExtension`, `ServerPlayer.openMenu` et une position écrite dans le buffer d'ouverture.
Les classes de rendu ne sont enregistrées que sur le client physique.

## Vérifications exécutées

- `./gradlew.bat build` : compilation et assemblage réussis après correction de l'ordre
  d'activation NeoForge dans le banc de test.
- `./gradlew.bat runGameTestServer` : **4 tests obligatoires réussis**, dans un vrai serveur
  Minecraft sans classes client chargées par le mod principal.
  - Placement et sérialisation/rechargement NBT du serveur.
  - Placement et sérialisation/rechargement NBT de l'écran.
  - Refus des étrangers, permission VIEWER, révocation, réseau supprimé.
  - Distance, désactivation, suppression du bloc et absence de transfert d'inventaire.
- `./gradlew.bat runClientSmoke` : **réussi**, vrai client Minecraft avec serveur intégré.
  Deux blocs synchronisés ; deux clics `useItemOn` transmis au serveur ; deux écrans
  maintenus ouverts dix ticks, bonne position reçue ; fermeture ; modèles des 24 états vérifiés.
  Marqueurs `HOMELINK_CLIENT_SMOKE_OK` et `HOMELINK_CLIENT_SMOKE_SHUTDOWN_OK` dans le journal.
- `test` n'a pas de sources JUnit : les tests fonctionnels sont les GameTests et le test client ci-dessus.

Journaux locaux : `build/validation/server/logs/latest.log` et
`build/validation/client/logs/latest.log`. Les sources de vérification, mondes et journaux
ne sont pas inclus dans le JAR distribuable. Les données d'exécution restent sous `build/`, ignoré par Git.

## Essai manuel

Lancer `./gradlew.bat runClient`, créer un monde créatif et utiliser l'onglet HomeLink Dashboard,
ou les commandes :

```text
/give @s homelink_dashboard:home_server
/give @s homelink_dashboard:dashboard_display
```

Placer chaque bloc avec le joueur, puis clic droit. Échap, la touche d'inventaire ou Fermer
ferment le menu. Le panneau se place sur une face verticale ou s'oriente face au joueur.
Le joueur qui a placé un bloc non associé peut ouvrir sa coque d'interface.
Un bloc posé par `/setblock` n'a pas de propriétaire : son ouverture est refusée.

Pour une instance Minecraft hors IDE, installer ensemble `homecore-1.0.0.jar` et
`homelink_dashboard-1.0.0-SNAPSHOT.jar`, sur client et serveur. HomeCore est obligatoire et non embarqué.

## Limites de cette phase

- Interface minimale uniquement ; pas de pages vides, appareils, snapshots, actions ou widgets.
- Création/sélection/association réseau en jeu et suivi des changements HomeCore : phase 2.
  Les champs persistants et contrôles nécessaires sont prêts ; aucun faux réseau n'est créé.
- Les blocs non associés sont OFFLINE. Le statut est recalculé aux mutations locales et aux
  tentatives d'ouverture ; pas encore de suivi spontané des modifications externes du réseau.
- Le panneau est autonome, sans contrainte de support mural ni lien physique à un serveur.
  L'association prévue est celle du HomeNetwork, autorisée par le cahier des charges.
- Textures vanilla temporaires ; recettes prévues en phase 8.
- Persistance vérifiée par aller-retour NBT, pas encore par redémarrage complet d'une sauvegarde.
- Le test client vérifie le chargement et l'ouverture réels, pas une comparaison visuelle par capture.
- Avertissements de dépréciation d'API vanilla sur certaines surcharges ; aucune erreur de compilation.
  Le premier GameTest a aussi signalé l'absence initiale de `server.properties`, puis l'a généré et a réussi.

## Fichiers modifiés

- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `src/main/templates/META-INF/neoforge.mods.toml`

## Fichiers créés

Sous `src/main/java/fr/lkdm/homelink/dashboard/` :

- `HomeLinkDashboard.java`
- `registry/DashboardRegistries.java`
- `block/AccessPointStatus.java`
- `block/AccessPointBlock.java`
- `block/HomeServerBlock.java`
- `block/DashboardDisplayBlock.java`
- `blockentity/AccessPointBlockEntity.java`
- `blockentity/HomeServerBlockEntity.java`
- `blockentity/DashboardDisplayBlockEntity.java`
- `server/DashboardAccess.java`
- `menu/DashboardMenu.java`
- `client/DashboardClient.java`
- `client/screen/DashboardScreen.java`

Sous `src/main/resources/` :

- `assets/homelink_dashboard/blockstates/home_server.json`
- `assets/homelink_dashboard/blockstates/dashboard_display.json`
- `assets/homelink_dashboard/models/block/home_server_online.json`
- `assets/homelink_dashboard/models/block/home_server_offline.json`
- `assets/homelink_dashboard/models/block/home_server_error.json`
- `assets/homelink_dashboard/models/block/dashboard_display_online.json`
- `assets/homelink_dashboard/models/block/dashboard_display_offline.json`
- `assets/homelink_dashboard/models/block/dashboard_display_error.json`
- `assets/homelink_dashboard/models/item/home_server.json`
- `assets/homelink_dashboard/models/item/dashboard_display.json`
- `assets/homelink_dashboard/lang/en_us.json`
- `assets/homelink_dashboard/lang/fr_fr.json`
- `data/homelink_dashboard/loot_table/blocks/home_server.json`
- `data/homelink_dashboard/loot_table/blocks/dashboard_display.json`
- `data/minecraft/tags/block/mineable/pickaxe.json`

Vérification et documentation :

- `src/verification/java/fr/lkdm/homelink/dashboard/verification/DashboardValidation.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/FoundationGameTests.java`
- `src/verification/java/fr/lkdm/homelink/dashboard/verification/DashboardClientSmoke.java`
- `src/verification/resources/META-INF/neoforge.mods.toml`
- `src/verification/resources/data/homelink_dashboard_validation/structure/empty.nbt`
- `docs/PHASE_1.md`

Squelette d'exemple remplacé (fichiers supprimés) :

- `src/main/java/CoreLink/homelink/Homelink.java`
- `src/main/java/CoreLink/homelink/Config.java`
- `src/main/resources/assets/homelink/lang/en_us.json`

Les changements déjà présents dans les fichiers d'outillage/Git du workspace ont été conservés.
La phase 2 attend explicitement **PHASE SUIVANTE**.
