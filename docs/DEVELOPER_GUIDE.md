# Guide développeur — HomeLink Dashboard 1.6.0

## Contrat et architecture

Dashboard est un consommateur générique de **HomeCore 1.14.0** (API `1.9.0`). Les classes HomeCore ne sont pas recopiées dans ce projet. Les imports d’intégration utilisent `fr.lkdm.homecore.api.*` ; aucun import de `fr.lkdm.homecore.internal.*` n’est nécessaire ou autorisé côté Dashboard.

Le rendu partagé provient exclusivement de `fr.lkdm.homecore.api.client.ui` : `HomeLinkTheme`, `HomeLinkUi`, `HomeLinkButton` et `HomeLinkScreenLayout`. `DashboardTheme` et `DashboardButton` sont supprimés. Les autres mods consomment directement ce kit sans dépendre de Dashboard ; ses vues et son état restent spécifiques à ce dépôt. Voir [la migration FR/EN](UI_MIGRATION.md).

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

Les blocs stockent seulement le propriétaire, l’UUID du réseau et leur état actif. Ils ne conservent pas une copie des appareils. Le serveur et les répéteurs émettent à 64 blocs dans leur dimension ; un écran est un terminal sans fonction de relais. RadioNetworkService suit les BlockEntities chargées et calcule les chaînes reliées à un serveur actif. Les graphes sont partagés entre les requêtes d’un tick et invalidés immédiatement lors des changements de topologie. Aucun chunk n’est chargé à distance.

HomeCore 1.3.0 expose `HomeNetworkManager.setReachabilityPolicy(id, predicate)` et `isReachable(network, device)`. Les contraintes s’ajoutent aux permissions, jamais ne les remplacent. HomeCore applique ces règles aux listes, snapshots, deltas, événements et actions. Les erreurs de politique refusent l’accès. Dashboard n’importe aucune classe interne HomeCore.

RadioNetworksSavedData conserve les UUID des réseaux physiques afin que retirer tous les émetteurs ne rétablisse jamais une portée illimitée. Un réseau externe jamais associé à un point HomeLink conserve son fonctionnement logique HomeCore. Les appareils sans position ET sans dimension sont logiques et exigent un émetteur serveur connecté ; une localisation partielle est refusée. Les appareils physiques utilisent une distance euclidienne inclusive de 64 blocs, dans la même dimension. Les métriques ne sont pas dupliquées et les appartenances ne sont pas modifiées par la portée.

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

HomeCore 1.11.0 ajoute au schéma des appareils `Switchable` et `Renamable` les actions standard `homecore:power` (TOGGLE, CONTROL) et `homecore:rename` (TEXT, CONFIGURE, 50 caractères), et transmet l'état `powered` dans le snapshot ; un changement de nom ou d'état renvoie le snapshot. `ActionPanel` les retire du carrousel et les place sur sa première ligne (bouton Allumer/Éteindre, champ de nom). `DeviceActionView.availableWhen` reprend la règle de HomeCore : ces deux actions restent disponibles tant que l'appareil n'est pas OFFLINE, les autres seulement ONLINE. Leurs libellés sont traduits côté client, car le serveur envoie des textes déjà résolus.

Les contrôles numériques utilisent les bornes et le pas du schéma. La quantification des sliders utilise `BigDecimal`, cohérente avec la validation décimale HomeCore, y compris pour de grandes bornes finies. Le serveur reste la source de vérité : le client envoie une demande, il n’applique pas un changement de métrique lui-même. Le résultat HomeCore est affiché après réception.

L’interface reflète les rôles de la politique HomeCore courante de référence. Les paquets serveur vérifient toujours l’identité réelle du joueur, la session, le réseau et les permissions. VIEW autorise la consultation et les favoris personnels ; CONTROL et la permission supplémentaire de l’action sont nécessaires à son exécution ; CONFIGURE protège les modifications de layout et l’association d’un point d’accès.

## Widgets et extensions

`DashboardWidget` stocke un UUID, un type, un UUID d’appareil, un identifiant de métrique ou d'action éventuel et la géométrie sur une grille de 12 colonnes et 64 lignes. Les types sont DEVICE_SUMMARY, METRIC, ACTION et ENERGY_BALANCE ; ce dernier utilise l'UUID réservé NETWORK. Le modèle accepte toujours 4 × 2 pour les profils existants, tandis que l'éditeur propose 3 × 2, 6 × 3 et 12 × 3. Les limites sont de 32 widgets et 64 favoris par profil, sans chevauchement.

Depuis la 1.4.0, `HomeDashboardView` ne déplace plus les widgets case par case : il trie la liste en ordre de lecture (`y`, puis `x`), applique l’insertion, le déplacement ou le changement de préréglage, puis recalcule toutes les positions par flux sur 12 colonnes avant `saveLayout`. Le format des profils et la validation serveur sont inchangés ; une ancienne disposition avec des trous est compactée à la première modification. `MetricRendererRegistry.decimal` arrondit toute valeur décimale affichée à deux chiffres.

En 1.5.0, `HomeEditorView` gère la recherche et l'édition ; `WidgetCards` rend les cartes et envoie les actions à un clic par le chemin HomeCore existant. Le serveur valide qu'un nouveau widget ACTION référence une action BUTTON ou TOGGLE du réseau. `EnergyBalance` calcule le bilan depuis les mesures publiques HomeLink Energy, sans dépendance Java directe ; il regroupe les mesures identiques à l'aide du nombre de batteries annoncé par le réseau câblé. Sans identifiant public de ce réseau, une sélection partielle de deux réseaux distincts aux mesures identiques peut encore sous-estimer la consommation.

`MetricRendererRegistry.register(ResourceLocation, MetricRenderer)` est un point d’extension Java **interne à Dashboard** pour associer un type de métrique à une présentation pure. Sa sortie contient une valeur textuelle et éventuellement une fraction de progression. Le rendu final reste contrôlé par Dashboard ; erreurs et types inconnus passent par un fallback. Son cache de présentation est borné à 1 024 entrées et invalidé lors d’un nouvel enregistrement de renderer.

La séparation des vues et du modèle prépare de futurs widgets spécialisés. La version 1.0 ne revendique pas d’API publique stabilisée de rendu de widgets tiers. Une intégration générique HomeCore reste suffisante et prioritaire.

## Résumé sur la façade

`DashboardDisplayBlock` utilise son tick serveur existant (20 ticks) pour appeler `DisplaySummaryService.refresh` sur le seul maître. Le service compose les favoris autorisés du profil du destinataire, ou du propriétaire si le partage est actif. Il ajoute les 12 premiers widgets du profil en ordre de lecture (`DisplaySummary.WidgetTile`) : mesures pour un résumé ou un widget METRIC, libellé pour ACTION, mesures agrégées pour ENERGY_BALANCE. Les valeurs sont filtrées selon le VIEW du destinataire. Le choix de partage est conservé dans la block entity maîtresse. Le registrar de `DisplaySummaryPayloads` passe en version `3` ; client et serveur doivent partager la 1.5.0. Aucun abonnement de menu ni chargement de chunk n'est requis. Les paquets `DisplaySummaryPayloads.Update` sont envoyés individuellement aux joueurs à 16 blocs maximum ; VIEW, appartenance, portée radio et alimentation sont revérifiées avant chaque envoi. Ils ne sont jamais distribués dans les tags de mise à jour du block entity.

`DisplaySummaryClient` conserve au plus 256 résumés pendant 45 ticks, associés à l'instance du maître et au monde courant. Éloignement, remplacement du bloc, changement de monde et déconnexion invalident le cache. `DashboardDisplayRenderer` dessine une seule surface couvrant le multibloc, avec une boîte de rendu englobant toutes ses cases. Les valeurs ne sont pas sauvegardées. Le transport et les droits de l'interface ouverte restent indépendants.

## Machines et découverte

`MachineSystems` regroupe côté client les appareils suivis par espace de noms de type (`homelink_energy`, `homelink_farm`, `homelink_quarry`, `homelink_storage`) et calcule les résumés à partir des identifiants publics des métriques ; le Dashboard ne dépend d'aucun de ces mods à la compilation. Stockage et Autres n'apparaissent que s'ils contiennent des appareils.

L'onglet Ajouter passe par `DiscoveryPayloads`, lié à la session de menu validée ; le réseau vient toujours de cette session. `MachineDiscoveryService` filtre `DashboardAPI.devices(server).getAll()` : position et dimension connues, couverture par `RadioNetworkService.covers`. Il exige MANAGE_NETWORK, masque le nom des réseaux que le joueur ne peut pas voir, renvoie au plus 128 entrées et ajoute via `DashboardAPI.bindDevice` (HomeCore 1.9.0), qui vérifie aussi les droits de la machine (`NetworkMember.canConfigure`). Les appareils sans `NetworkMember` sont listés comme non compatibles. Les types partagés avec le client sont dans `network/MachineListing`.

## Persistance

`DashboardPreferencesSavedData` est une `SavedData` du stockage de l’Overworld, avec une clé composée de l’UUID du joueur et de l’UUID du réseau. Son fichier est `data/homelink_dashboard_preferences.dat` à la racine du monde. Le format a une version explicite ; les transactions client sont validées avant sauvegarde. Le serveur conserve au maximum 4 096 profils, dont 64 réseaux par joueur. Les fichiers globalement illisibles ne sont pas écrasés automatiquement.

HomeCore gère séparément `data/homecore_networks.dat`. Les associations des points d’accès figurent dans le NBT de leurs chunks (`Owner`, `HomeNetwork`, `Active`). Les préférences purement visuelles sont dans `config/homelink_dashboard-client.toml`. Aucun snapshot d’appareil n’est sauvegardé dans les profils Dashboard.

## Construire le projet

Java 21 est nécessaire. Les versions de référence sont dans `gradle.properties` et `build.gradle` : Minecraft 1.21.1, NeoForge 21.1.250, Dashboard 1.5.0, HomeCore 1.11.0 (API 1.7.0). Les recettes de Dashboard référencent `homecore:homelink_circuit_board`, `homecore:homelink_microprocessor` et `homecore:homelink_communication_module` par identifiant, sans importer de classe HomeCore. Conserver les versions de mappings du projet à moins d’une migration explicite.

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

## Noms des réseaux

HomeCore 1.3.0 expose HomeNetworkManager.renameNetwork : remplacement du snapshot immuable, conservation de l'identité et notification de persistance uniquement si le nom change. Dashboard utilise un payload de nom borné à 128 caractères. Le réseau cible est déduit du menu serveur courant ; les identités et permissions ne viennent jamais du client. Création : propriétaire du serveur non associé. Renommage : VIEW et MANAGE_NETWORK, menu vivant et point accessible. Les mutations partagent la limite de deux opérations par seconde. Le listener de résultat est supprimé à la fermeture de l'écran.
