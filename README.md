# HomeLink Dashboard 1.2.0

Centre de contrôle visuel générique de l’écosystème HomeLink : consulter les appareils HomeCore, exécuter leurs actions autorisées, recevoir leurs événements et organiser un tableau de bord personnel.

Dashboard interprète les schémas publics HomeCore. Il ne contient aucune intégration spécifique à Farm Monitor, Holographique Map, Create ou Mekanism. Son interface partage le cadre, la palette et les contrôles de [HomeLink Storage](https://github.com/LKDM7/HomeLink-Storage).

## Installation

| Composant | Version de référence |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.250 |
| Java | 21 |
| HomeCore, obligatoire | 1.7.0 ou plus récent (< 2.0.0) |
| HomeLink Dashboard | 1.2.0 |

Installer `homecore-1.7.0.jar` et `homelink_dashboard-1.2.0.jar` dans le dossier `mods` du client **et** du serveur. En solo, les deux JAR sont nécessaires dans l’installation Minecraft. HomeCore reste une dépendance séparée ; son code n’est pas embarqué dans le JAR Dashboard.

**English installation:** use Minecraft 1.21.1, NeoForge 21.1.250 and Java 21. Put both HomeCore 1.7.0 and HomeLink Dashboard 1.2.0 in the client and server `mods` folders. Crafting recipes use the HomeLink Circuit Board, Microprocessor and Communication Module assembled at the HomeCore Electronics Workbench. Place a HomeLink Server, create or link a HomeNetwork, then link a Dashboard Display to the same network. Extend the 64-block range with HomeLink Repeaters. Devices must be exposed by a HomeCore-compatible mod. The **?** button in the header opens the in-game manual.

## Nouveautés de la 1.2.0

- Le serveur et le répéteur demandent le **module de communication HomeLink** de HomeCore 1.7.0 à la place du circuit imprimé ; requiert HomeCore 1.7.0.

## Nouveautés de la 1.1.0

- Recettes basées sur les composants HomeCore (circuit imprimé et microprocesseur), requiert HomeCore 1.6.1.
- Interface alignée sur HomeLink Storage : contrôles de 18 pixels, info-bulles, bouton **?** dans l’en-tête.
- Manuel intégré bilingue, portée radio de 64 blocs avec répéteurs, réseaux renommables.

Détails : [notes de mise à jour](docs/UPDATE_NOTES.md) et [portée et répéteurs](docs/RADIO.md).

## Fabrication

Les blocs se fabriquent en table de fabrication. Leurs composants s’assemblent dans l’**Établi électronique HomeLink** de HomeCore.

| Bloc | Ingrédients |
| --- | --- |
| HomeLink Server | 5 lingots de fer, 1 microprocesseur HomeLink, 1 module de communication HomeLink, 2 redstone |
| HomeLink Dashboard | 5 lingots de fer, 3 verres, 1 circuit imprimé HomeLink |
| Répéteur HomeLink | 4 lingots de fer, 1 répéteur de redstone, 1 module de communication HomeLink |

Les grilles détaillées figurent dans le [guide utilisateur](docs/USER_GUIDE.md#recettes).

## Première utilisation

1. Placer un **HomeLink Server** et faire un clic droit.
2. Créer un HomeNetwork (nom personnalisable) ou choisir un réseau existant pour lequel vous avez les droits de configuration.
3. Placer un **HomeLink Dashboard** à moins de 64 blocs et l’associer au même réseau. Au-delà, poser des **Répéteurs HomeLink** associés à ce réseau.
4. Utiliser **HOME**, **DEVICES**, **ALERTS**, **NETWORK** et **SETTINGS** ; le bouton **?** ouvre le manuel.

Les appareils proviennent des mods compatibles HomeCore. Un réseau neuf peut donc être vide. Les exemples et appareils de vérification ne sont pas inclus dans le JAR de production.

Voir le [guide utilisateur](docs/USER_GUIDE.md) pour les favoris, widgets, permissions, recettes et dépannage ; le [guide développeur](docs/DEVELOPER_GUIDE.md) pour l’architecture et les API effectivement utilisées.

## Compilation et vérification

Le dépôt utilise le wrapper Gradle et le projet source HomeCore voisin :

```text
workspace/
├── HomeCore/
└── HomeLink/
```

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runPerformanceSmoke
```

Un autre emplacement peut être fourni avec `-Phomecore_dir=C:/chemin/HomeCore`. Le JAR Dashboard est produit dans `build/libs/`. `build` compile aussi les classes de vérification ; il ne lance pas automatiquement Minecraft ni les GameTests. Les commandes complètes figurent dans le [guide développeur](docs/DEVELOPER_GUIDE.md#vérification).

Le scénario en jeu de performance vérifie **100 appareils et 800 métriques** avec synchronisation réelle. Il s’agit d’un test fonctionnel sous charge, pas d’une garantie de fréquence d’images. Le suivi réseau courant est plafonné à 128 appareils ; le surplus est signalé dans l’interface.

## Rapports de réalisation

| Phase | Rapport |
| --- | --- |
| 1 — Foundation | [PHASE_1](docs/PHASE_1.md) |
| 2 — Connexion HomeCore | [PHASE_2](docs/PHASE_2.md) |
| 3 — Device Explorer | [PHASE_3](docs/PHASE_3.md) |
| 4 — Device Control | [PHASE_4](docs/PHASE_4.md) |
| 5 — Home et widgets | [PHASE_5](docs/PHASE_5.md) |
| 6 — Alertes et réseau | [PHASE_6](docs/PHASE_6.md) |
| 7 — Finition et performances | [PHASE_7](docs/PHASE_7.md) |
| 8 — Release | [PHASE_8](docs/PHASE_8.md) |

Les rapports antérieurs décrivent l’état de leur phase ; les guides de cette version décrivent le fonctionnement final.

Licence : [Apache License 2.0](LICENSE).
