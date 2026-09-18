# Guide développeur — HomeLink Dashboard 1.0.0

## Contrat et architecture

Dashboard est un consommateur générique de **HomeCore 1.1.0**. Les classes HomeCore ne sont pas recopiées dans ce projet. Les imports d’intégration utilisent `fr.lkdm.homecore.api.*` ; aucun import de `fr.lkdm.homecore.internal.*` n’est nécessaire ou autorisé côté Dashboard.

```text
Mod fournisseur / adaptateur HomeCore
  → DashboardDevice + DeviceSchema + DeviceMetric + DeviceAction + DeviceEvent
  → HomeCore : registre, réseau, permissions, validation, transport
  → DashboardClientState
  → vues Dashboard et contrôles génériques
```

| Zone | Responsabilité |
| --- | --- |
| `block`, `blockentity`, `registry` | Deux points d’accès physiques, enregistrement et données locales persistantes |
| `menu`, `server` | Session authentifiée, distance, droits, création/association de réseau, profils serveur |
| `network` | Contrat de session et messages de préférences Dashboard |
| `client/state` | État HomeCore reçu, suivi des réponses et préférences confirmées |
| `client/screen`, `client/widget` | Navigation, listes visibles, formulaires d’actions et grille |
| `client/rendering` | Présentation des métriques selon leur type public |
| `dashboard/layout`, `dashboard/widget` | Modèle de profils et widgets référencés par UUID |
| `config` | Paramètres visuels client |

Les blocs stockent seulement le propriétaire, l’UUID du réseau et leur état actif. Ils ne conservent pas une copie des appareils. Le Display est relié au HomeNetwork, pas à une simulation de liaison radio ou à un Server particulier.

## API HomeCore effectivement utilisée

Ces entrées existent dans l’API publique du projet HomeCore associé :

| Entrée | Usage |
| --- | --- |
| `DashboardAPI.networks(MinecraftServer)` | Gestionnaire des réseaux persistants |
| `DashboardAPI.devices(MinecraftServer)` | Registre des appareils vivants de ce serveur |
| `DashboardAPI.events(MinecraftServer)` | Bus d’événements des appareils |
| `DashboardAPI.hasPermission(ServerPlayer, UUID, Permission)` | Contrôle d’autorisation authentifié |
| `DashboardAPI.registerDeviceProvider(BlockEntityType<T>, DeviceProvider<? super T>)` | Déclaration d’un adaptateur de BlockEntity |
| `HomeCoreClient.subscribeNetwork(UUID)` | Abonnement réseau borné, API 1.1 |
| `HomeCoreClient.executeAction(UUID, UUID, ResourceLocation, Object)` | Demande d’exécution côté serveur |
| `HomeCoreClient.unsubscribe(UUID)` | Fin d’abonnement et nettoyage du cache réseau |
| `ClientDeviceCache.INSTANCE.listen(Consumer<CustomPacketPayload>)` | Réception client ; handle `AutoCloseable` à fermer |

Pour un appareil logique indépendant d’un bloc, une intégration peut appeler `DashboardAPI.devices(server).register(device)` sur le thread serveur, puis `DashboardAPI.networks(server).addDevice(networkId, device.id())` dans un contexte de gestion autorisé. La création des appareils et leur rattachement sont la responsabilité du fournisseur. Pour un appareil de BlockEntity, utiliser le mécanisme de providers HomeCore et son cycle de vie documenté dans le projet HomeCore.

`DashboardDevice` exige une identité et des définitions stables tant qu’il est enregistré. Les valeurs des `DeviceMetric` évoluent ; un changement de schéma doit respecter le cycle de réenregistrement HomeCore. Une intégration doit retirer ses appareils lorsque leur support disparaît et publier uniquement ses événements déclarés.

Une intégration Create, Mekanism ou autre appartient à un adaptateur HomeCore. Dashboard n’a pas besoin de connaître le mod d’origine pour afficher les valeurs et actions transportables de son appareil.

## Synchronisation 1.1

HomeCore 1.0 ne fournissait qu’une page active de 16 appareils. La V1 finale Dashboard requiert l’API publique 1.1 de suivi réseau pour consulter au moins 100 appareils simultanément sans créer une boucle de polling client.

HomeCore 1.1 utilise le protocole de transport **2**. Installer cette version des deux côtés ; conserver les anciennes méthodes Java de pagination ne rend pas son protocole compatible avec un client HomeCore 1.0.

`subscribeNetwork` renvoie un UUID de corrélation. `NetworkWatchResponse` donne une liste d’au plus 128 appareils, le nombre total et l’indication de dépassement. HomeCore envoie ensuite les `DeviceSnapshot`, puis uniquement les `MetricUpdate` nécessaires, les modifications de roster et les `DeviceEventNotification`. Les changements d’état ou de fournisseur déclenchent un snapshot ciblé.

`DashboardClientState` expose une vue en lecture seule à ses consommateurs, corrèle les demandes et réponses, applique les révisions métriques croissantes et rejette les réponses étrangères. La révision de structure est distincte de celle des valeurs : un delta ne relance pas le tri ou le filtrage des appareils. La recherche s’effectue sur les données autorisées reçues.

HomeCore plafonne la livraison par observateur à 16 snapshots et 256 deltas par tick ; les événements sont limités à 16 par tick et 32 par seconde. Les deltas sont distribués avec reprise de parcours pour éviter qu’un appareil très actif bloque durablement les suivants. Dashboard conserve au plus 512 alertes selon sa configuration, 128 par défaut. Les historiques de session, caches et listeners sont libérés à la fermeture ou à la déconnexion.

Le transport historique de pages reste disponible dans le seam de test `DashboardClientState.Transport`. Le transport réel utilise le suivi réseau 1.1.

## Actions et autorité

`ActionControlRegistry` construit les contrôles à partir de `DeviceActionView`, décodé du schéma HomeCore. Les types pris en charge sont BUTTON (`Unit.INSTANCE`), TOGGLE, INTEGER, DOUBLE, SLIDER, SELECT, TEXT et POSITION (`BlockPos`). Une définition inconnue ou invalide désactive le contrôle.

Les contrôles numériques utilisent les bornes et le pas du schéma. La quantification des sliders utilise `BigDecimal`, cohérente avec la validation décimale HomeCore, y compris pour de grandes bornes finies. Le serveur reste la source de vérité : le client envoie une demande, il n’applique pas un changement de métrique lui-même. Le résultat HomeCore est affiché après réception.

L’interface reflète les rôles de la politique HomeCore courante de référence. Les paquets serveur vérifient toujours l’identité réelle du joueur, la session, le réseau et les permissions. VIEW autorise la consultation et les favoris personnels ; CONTROL et la permission supplémentaire de l’action sont nécessaires à son exécution ; CONFIGURE protège les modifications de layout et l’association d’un point d’accès.

## Widgets et extensions

`DashboardWidget` stocke un UUID, un type, un UUID d’appareil, un identifiant de métrique éventuel et la géométrie sur une grille de 12 colonnes et 64 lignes. Les types V1 sont DEVICE_SUMMARY et METRIC. Le modèle valide 3 × 2, 4 × 2, 6 × 3 et 12 × 3 ; l’éditeur V1 propose l’ajout en 6 × 3 et l’alternance 6 × 3 / 12 × 3. Les limites sont de 32 widgets et 64 favoris par profil, sans chevauchement.

`MetricRendererRegistry.register(ResourceLocation, MetricRenderer)` est un point d’extension Java **interne à Dashboard** pour associer un type de métrique à une présentation pure. Sa sortie contient une valeur textuelle et éventuellement une fraction de progression. Le rendu final reste contrôlé par Dashboard ; erreurs et types inconnus passent par un fallback. Son cache de présentation est borné à 1 024 entrées et invalidé lors d’un nouvel enregistrement de renderer.

La séparation des vues et du modèle prépare de futurs widgets spécialisés. La version 1.0 ne revendique pas d’API publique stabilisée de rendu de widgets tiers. Une intégration générique HomeCore reste suffisante et prioritaire.

## Persistance

`DashboardPreferencesSavedData` est une `SavedData` du stockage de l’Overworld, avec une clé composée de l’UUID du joueur et de l’UUID du réseau. Son fichier est `data/homelink_dashboard_preferences.dat` à la racine du monde. Le format a une version explicite ; les transactions client sont validées avant sauvegarde. Le serveur conserve au maximum 4 096 profils, dont 64 réseaux par joueur. Les fichiers globalement illisibles ne sont pas écrasés automatiquement.

HomeCore gère séparément `data/homecore_networks.dat`. Les associations des points d’accès figurent dans le NBT de leurs chunks (`Owner`, `HomeNetwork`, `Active`). Les préférences purement visuelles sont dans `config/homelink_dashboard-client.toml`. Aucun snapshot d’appareil n’est sauvegardé dans les profils Dashboard.

## Construire le projet

Java 21 est nécessaire. Les versions de référence sont dans `gradle.properties` et `build.gradle` : Minecraft 1.21.1, NeoForge 21.1.250, Dashboard 1.0.0, HomeCore 1.1.0. Conserver les versions de mappings du projet à moins d’une migration explicite.

`settings.gradle` inclut réellement le build source `../HomeCore` et substitue `fr.lkdm.homecore:homecore`. Ce projet source est requis avec la configuration actuelle ; un JAR isolé placé arbitrairement dans `libs` ne remplace pas cette configuration.

```powershell
.\gradlew.bat build
.\gradlew.bat "-Phomecore_dir=C:/workspace/HomeCore" build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Les JAR de production sortent dans `build/libs/` pour Dashboard et dans `build/libs/` du projet HomeCore pour sa dépendance. Le source set `verification` est réservé aux GameTests et aux scénarios clients : ses appareils et son mod de validation ne sont pas embarqués dans le JAR Dashboard.

## Vérification

Exécuter les clients successivement, car ils utilisent le même affichage et le GPU. Les tâches Gradle de test en jeu attendent des marqueurs de réussite réels dans les journaux ; un client qui quitte sans ce marqueur fait échouer la tâche.

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat runClientSmoke
.\gradlew.bat runConnectionSmoke
.\gradlew.bat runActionSmoke
.\gradlew.bat runPreferencesSmoke
.\gradlew.bat runAlertsSmoke
.\gradlew.bat runPerformanceSmoke
.\gradlew.bat runPersistence -PpersistencePass=write
.\gradlew.bat runPersistence -PpersistencePass=read
```

`test` ne remplace pas `runGameTestServer` : les tests Dashboard actuels sont dans `src/verification`, pas dans un source set JUnit `src/test`. `build` compile ces classes mais ne lance pas automatiquement les scénarios Minecraft. Les deux passes de persistance doivent être exécutées dans cet ordre, en deux processus successifs ; elles partagent le monde de vérification conservé sous `build/validation/persistence`.

Les résultats se trouvent sous `build/validation/<scénario>/logs/latest.log` et les captures sous les répertoires de captures correspondants. Le scénario de performance utilise 100 appareils et 800 métriques, vérifie les deltas et le cycle de vie de l’interface. Ce test n’est pas un benchmark FPS universel.

Les vérifications propres au transport HomeCore se lancent depuis son projet, avec son wrapper et ses tests. Les commandes exécutées, résultats et limites de cette livraison sont consignés dans [PHASE_7.md](PHASE_7.md) et [PHASE_8.md](PHASE_8.md). Les phases antérieures restent disponibles via le [README](../README.md#rapports-de-réalisation).
## Vérifier la traduction française

Les libellés fixes sont dans `assets/homelink_dashboard/lang/fr_fr.json` et `en_us.json`. `DashboardText` traduit les codes uniquement au moment de l'affichage ; les valeurs utilisées par le protocole, les filtres et les permissions restent inchangées. Les codes inconnus gardent leur texte d'origine.

Les profils de vérification acceptent `-PsmokeLanguage=fr_fr`, par exemple `gradlew.bat runActionSmoke runAlertsSmoke runConnectionSmoke -PsmokeLanguage=fr_fr`. Cette option définit la langue des clients de test sans changer les préférences du client de jeu normal. Le scénario `runPreferencesSmoke` vérifie également les états, rôles, interrupteurs, résultats et caractères français lorsque cette langue est chargée.
