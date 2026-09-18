# Audit de livraison — HomeLink Dashboard 1.0

Audit de sources et de compatibilité effectué le 18 septembre 2026, avant le build
release final. Aucun changement des versions Minecraft/NeoForge ou des mappings
n'a été effectué pendant cet audit.

| Point vérifié | Constat |
| --- | --- |
| Intégration générique | Les imports HomeCore du code livré passent uniquement par `fr.lkdm.homecore.api.*`. Aucun import de ses packages `internal` ou `network`, aucune intégration conditionnelle Farm Monitor, Holographic Map, Create ou Mekanism. |
| Compatibilité | Dashboard requiert HomeCore **1.1.0**. Les anciennes APIs Java paginées restent présentes ; le protocole HomeCore **2** doit être installé des deux côtés. HomeCore 1.0.0 ne suffit plus. |
| Séparation des côtés | Les écrans et leurs enregistrements restent dans le client. Le transport de préférences partagé transmet des données et appelle des listeners sans charger de classe GUI. Le démarrage du serveur GameTest vérifie le chargement serveur réel. |
| Synchronisation | Abonnement réseau unique, jusqu'à 128 appareils ; snapshots initiaux étalés, Metrics modifiées envoyées en deltas, changements de liste et de rôle poussés. Budgets : 16 snapshots et 256 deltas par tick/joueur ; distribution équitable testée sous charge continue. |
| Autorisations | HomeCore revalide VIEW/CONTROL/permission d'action et ses paramètres. Les préférences revérifient le menu, son identifiant, son réseau et sa validité ; CONFIGURE pour le layout, VIEW pour les favoris personnels. |
| Persistance | Profils par joueur et réseau, références de widgets/favoris seulement ; HomeCore conserve les appareils et réseaux. Les associations des deux blocs, widgets et favoris ont été relus après arrêt et démarrage dans une seconde JVM. |
| Données tierces | Texte de présentation trop long tronqué ; identifiants et budgets structurels stricts. Provider défaillant isolé par un état ERROR, données inconnues affichées avec fallback. |
| Archive intermédiaire inspectée | `homelink_dashboard-1.0.0-SNAPSHOT.jar` : 105 entrées, aucune classe HomeCore embarquée, fixture de vérification, JUnit, ancien package CoreLink, configuration locale ou journal. Le contrôle du JAR release final est distinct. |

## Preuves disponibles

- HomeCore : `gradlew.bat build test` réussi, **85 tests JUnit**, dont codecs,
  cache, 100 appareils, 128 × 32 Metrics en changement continu, permissions,
  récupération d'un provider et limitation des messages.
- Dashboard : **32 GameTests requis réussis**, confirmés par
  `build/validation/persistence/logs/latest.log`.
- Client Minecraft réel : marqueur `HOMELINK_PERFORMANCE_SMOKE_OK` dans
  `build/validation/performance/logs/latest.log` : **100 appareils, 800 Metrics**,
  deltas sans rechargement complet, recherche locale et par capability, liste
  dynamique, Offline, événements au-delà des 16 premiers appareils, tailles
  320×240 et 640×360, trois réouvertures et listener fermé devenu inactif.
- Redémarrage réel : marqueur `HOMELINK_PERSISTENCE_READ_OK`, deux blocs,
  un widget et un favori conservés dans une seconde JVM.

Les rapports des phases précédentes constituent un historique : leurs anciennes
limites de 16 appareils et leurs nombres de tests ne décrivent pas la version finale.

## Limites explicites

- Au-delà de **128 appareils actifs**, la liste suivie est tronquée et l'interface
  le signale. Les alertes couvrent néanmoins le réseau autorisé ; une source située
  hors de cette liste ne peut pas ouvrir sa fiche depuis l'alerte. Les résumés de
  statut et la recherche portent sur les appareils effectivement synchronisés.
- Les alertes sont temporaires, bornées à 128 par défaut et au maximum 512 ; elles
  ne constituent pas un historique persistant.
- Les profils sont bornés à 32 widgets et 64 favoris ; grille de 12 colonnes et
  64 lignes. Le stockage limite à 64 réseaux par joueur et 4096 profils par monde.
- L'enregistrement d'un widget Metric exige que sa définition soit actuellement
  disponible dans HomeCore. Les références sauvegardées devenues indisponibles
  restent affichables sous forme d'état manquant.
- Les écrans physiques utilisent un rendu de bloc simple. Pas de tablette,
  multibloc géant, caméra ou automatisation avancée dans cette release.
- Les essais client utilisent les runs NeoForge du workspace. Cet audit ne
  prétend pas qu'une installation séparée dans un launcher externe a été testée.

## Contrôle release final

Le build final a réussi : `homelink_dashboard-1.0.0.jar`, 82 clés EN/FR,
licence Apache 2.0 embarquée, dépendance HomeCore 1.1.0 et bornes Minecraft
`[1.21.1]` / NeoForge `[21.1.250,21.2)`. Le contrôle d'archive exclut les classes
HomeCore et les fixtures. Les **34 GameTests** finaux passent, avec les recettes
et loot tables ; le scénario HOME en français a été exécuté en jeu. Voir
[PHASE_8](PHASE_8.md) et `build/phase8-release-validation.log`.
