# HomeLink Dashboard 1.5.1

Centre de contrôle visuel générique de l’écosystème HomeLink : consulter les appareils HomeCore, exécuter leurs actions autorisées, recevoir leurs événements et organiser un tableau de bord personnel.

Dashboard interprète les schémas publics HomeCore. Il ne contient aucune intégration spécifique à Farm Monitor, Holographique Map, Create ou Mekanism. Son interface partage le cadre, la palette et les contrôles de [HomeLink Storage](https://github.com/LKDM7/HomeLink-Storage).

## Installation

| Composant | Version de référence |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.250 |
| Java | 21 |
| HomeCore, obligatoire | 1.12.0 ou plus récent (< 2.0.0) |
| HomeLink Energy, obligatoire | 0.4.1 ou plus récent (< 1.0.0) |
| HomeLink Dashboard | 1.5.1 |

Installer `homecore-1.12.0.jar`, `homelink_energy-0.4.1.jar` et `homelink_dashboard-1.5.1.jar` dans le dossier `mods` du client **et** du serveur. En solo, ces JAR sont nécessaires dans l’installation Minecraft. HomeCore reste une dépendance séparée ; son code n’est pas embarqué dans le JAR Dashboard.

**English installation:** use Minecraft 1.21.1, NeoForge 21.1.250 and Java 21. Put HomeCore 1.12.0, HomeLink Energy 0.4.1 and HomeLink Dashboard 1.5.1 in the client and server `mods` folders. Crafting recipes use the HomeLink Circuit Board, Microprocessor and Communication Module assembled at the HomeCore Electronics Workbench. Place a HomeLink Server, create or link a HomeNetwork, then link a Dashboard Display to the same network. Extend the 64-block range with HomeLink Repeaters. Devices must be exposed by a HomeCore-compatible mod. The **Machines** tab summarizes machines by system and the **Add** tab adds machines in radio range to the network. The **?** button in the header opens the in-game manual.

## Nouveautés de la 1.5.1

- Requiert HomeCore 1.12.0 et HomeLink Energy 0.4.1. Les dépendances se résolvent depuis les paquets GitHub publiés ; les sources voisines (`../HomeCore`, `../HomeLinkEnergy`) restent utilisées si elles sont présentes, avec contrôle de version (`-PuseLocalDependencies=false` pour les ignorer).
- Intégration continue (build, tests unitaires, archive et GameTests serveur) et premiers tests unitaires.

## Nouveautés de la 1.5.0

- L'éditeur HOME propose une recherche et regroupe les appareils par système. Les widgets ont trois tailles : quart, moitié et pleine largeur.
- Un widget peut afficher une action à un clic (bouton ou interrupteur) directement sur HOME. Les actions qui demandent une valeur restent dans la fiche de l'appareil.
- Le widget **Bilan énergie** réunit production, consommation et charge des batteries HomeLink Energy du réseau.
- Le propriétaire d'un écran mural peut choisir entre l'accueil personnel de chaque spectateur et son propre accueil partagé. Les droits de consultation de chaque spectateur restent appliqués.
- En haut de l'écran **Actions** d'une machine : **Allumer**/**Éteindre** et un champ pour la **renommer** (✓ ou Entrée ; vide = nom d'origine). Les machines sans interrupteur, comme les batteries, se renomment aussi. Renommer demande le droit CONFIGURE ; une machine éteinte reste pilotable pour être rallumée.
- Requiert HomeCore 1.11.0 (actions standard `homecore:power` et `homecore:rename`). Avec Energy 0.3.0, Farm 1.3.0 et Quarry 1.3.0 : panneaux solaires, éoliennes, pompes, stations FarmBot et carrières s'allument et s'éteignent ; toutes leurs machines se renomment.
- Le format du résumé d'écran a changé : installer la 1.5.0 sur le serveur et tous les clients.

## Nouveautés de la 1.4.0

- Accueil simplifié : un seul bouton **Modifier** ouvre l’éditeur. Glisser un appareil de la liste sur l’aperçu pour l’ajouter, glisser un widget pour le déplacer ; les widgets se rangent seuls. Cliquer un widget pour choisir ce qu’il affiche (résumé ou une mesure), sa largeur, ou le supprimer.
- L’écran mural reprend vos widgets dans la disposition de l’accueil ; sans widget, il affiche les favoris comme avant.
- Valeurs affichées avec deux décimales au plus.
- Correction : taper un nom de réseau (touche E comprise) ne ferme plus l’interface.
- Serveur et clients doivent utiliser la même version 1.4.0 (format du résumé d’écran modifié).

## Nouveautés de la 1.3.0

- Onglet **Machines** : résumé par système (Énergie, Ferme, Carrière ; Stockage s’il est sur le réseau), avec navigation et accès à la fiche de chaque machine.
- Onglet **Ajouter** : liste les machines dans la zone radio (serveur et répéteurs), avec leur nom, et les ajoute au réseau ; requiert `MANAGE_NETWORK`.
- Écran en **trois tailles** (1 × 1, 2 × 1, 2 × 2), choisies comme un tableau ; Maj + clic force le 1 × 1. La façade affiche vos favoris en direct.
- Nom de réseau proposé unique et avertissement en cas de doublon.
- Requiert HomeCore 1.9.0. Les machines d’Energy 0.2.0, Farm 1.1.0 et Quarry 1.1.0 s’ajoutent depuis le Dashboard.

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
