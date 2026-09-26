# Guide utilisateur — HomeLink Dashboard 1.1.0

## Préparer le jeu

Installer Minecraft 1.21.1, NeoForge 21.1.250 et Java 21. Les deux mods **HomeCore 1.6.1** et **HomeLink Dashboard 1.1.0** doivent être présents côté client et côté serveur. Les traductions françaises et anglaises suivent la langue choisie dans Minecraft.

Pour l'interface française, choisir **Français (France)** dans les langues de Minecraft. Les menus, états d'appareils, rôles, filtres, interrupteurs et résultats d'action sont traduits. Les noms personnalisés, messages et intitulés de mesures fournis par un autre mod restent ceux de ce mod ; Dashboard ne traduit pas arbitrairement les données reçues. Les codes inconnus conservent un affichage de secours lisible.

**English quick start:** install both mods on client and server. Right-click a HomeLink Server to create or select a HomeNetwork. Place a Dashboard Display and link it to that network. HOME contains your personal favorites and widgets; DEVICES lists compatible devices; ALERTS shows live events; NETWORK shows connection details; SETTINGS changes local display preferences.

## Manuel intégré

Le bouton **?**, en haut à droite de l’en-tête (même emplacement que dans HomeLink Storage), ouvre le manuel dans le jeu, même avant l’association à un réseau. Cinq chapitres expliquent les premiers pas, les appareils et actions, les favoris et la disposition, les alertes et permissions, puis le dépannage. La langue suit celle de Minecraft (français ou anglais).

Les flèches **< / >** changent de chapitre. La molette et les boutons **Haut / Bas** font défiler le texte ; ces boutons sont également accessibles au clavier. Raccourcis : **Page préc. / Page suiv.** pour défiler, **Début / Fin** pour aller au début ou à la fin du chapitre, **← / →** pour changer de chapitre. **Retour** ou **Échap** revient à la page précédente sans fermer la connexion au réseau. Les mesures continuent de se mettre à jour pendant la lecture. **Fermer** quitte le Dashboard.

Chaque étape possède un titre sur bandeau gris et un repère ambre. Les noms de commandes et permissions sont mis en couleur ; le texte courant reste clair, avec un interligne et des séparations entre les sections pour faciliter la lecture.

## Créer le point d’accès

Dans l’onglet créatif HomeLink Dashboard, les deux blocs sont **HomeLink Server** et **HomeLink Dashboard**. Ils disposent également de recettes de fabrication.

1. Placer le serveur en laissant deux blocs de hauteur libres. Un seul objet place les deux moitiés ; le joueur qui le pose devient propriétaire de ce point d’accès.
2. Faire un clic droit. Choisir **Créer un HomeNetwork** pour créer un réseau nommé automatiquement à partir du joueur, ou sélectionner un réseau existant puis l’associer.
3. Placer le Dashboard Display et ouvrir sa configuration. Sélectionner le même réseau et confirmer l’association. Le Display peut associer un réseau existant ; la création d’un réseau se fait sur le Server.
4. Faire un clic droit sur l’un des deux blocs associés pour ouvrir la même interface réseau.

Le Display possède une façade rectangulaire et un voyant d'état. Son petit schéma de façade est décoratif ; les données réelles sont dans l'interface ouverte au clic. La GUI prend la forme d'un terminal industriel gris : boutons en relief, onglet actif enfoncé et focus clavier visible. Elle partage le cadre, la palette, les contrôles de 18 pixels, les info-bulles et la disposition de l’en-tête (voyant d’état puis **?**) avec HomeLink Storage. HOME regroupe ses compteurs sur une ligne lorsque la fenêtre est assez grande et présente les favoris dans des lignes compactes. La taille de l'interface suit le réglage d'échelle GUI de Minecraft.

Les points d’accès utilisent le même HomeNetwork. Le serveur émet dans un rayon de **64 blocs**, hauteur comprise, dans sa dimension. L’écran et les appareils physiques doivent être couverts par un serveur ou un répéteur connecté. Aucun câble n’est nécessaire et les murs ne bloquent pas le signal. Le joueur doit rester à moins de huit blocs du point d’accès utilisé ; le menu se ferme si ce point est détruit, désactivé ou perd le signal.

### Étendre le signal

Fabriquer un **Répéteur HomeLink**, le placer à 64 blocs maximum d’un émetteur connecté, puis faire un clic droit et l’associer au même réseau. Son voyant vert confirme la liaison ; gris indique l’absence de signal, rouge un réseau supprimé. Plusieurs répéteurs peuvent se relayer : serveur → relais à 64 blocs → relais à 128 blocs → appareil à 192 blocs, si les positions sont alignées. Un écran ne relaie pas le signal.

Le serveur et les relais doivent rester dans des chunks chargés. Ils ne chargent pas le monde à distance et ne traversent pas les dimensions. Une chaîne sans serveur actif ne transmet rien. Un relais appartenant à un autre réseau ne prolonge pas le vôtre. Les associations sont conservées après redémarrage.

Les appareils doivent toujours être enregistrés et associés au réseau par leur mod HomeCore : il n’y a pas de détection automatique des blocs voisins. Les appareils physiques hors portée sont retirés de la liste en environ une seconde ; leurs actions, mesures et événements sont bloqués côté serveur dès la perte du signal. Ils réapparaissent quand la liaison revient. Leurs favoris et widgets restent enregistrés. Les appareils purement logiques (sans position ni dimension) restent accessibles lorsqu’un serveur de leur réseau est chargé ; une localisation partielle est refusée.

Cette fonctionnalité exige **HomeCore 1.6.1** ou une version compatible plus récente sur le client et le serveur. Mettre à jour les deux JAR ensemble.

Un point non associé ou hors portée est hors ligne. Un point associé à un réseau existant et recevant le signal est en ligne. Une association vers un réseau supprimé donne une erreur. Ces états indiquent la disponibilité du point d’accès, pas la santé de chacun des appareils du réseau.

Le serveur possède trois baies et des voyants animés : vert et orange pour l’activité visuelle, bleu pour le panneau, rouge clignotant en erreur. Hors ligne, les voyants restent gris. Le clignotement est décoratif et ne représente pas un débit réel HomeCore. Cliquer sur l’une ou l’autre moitié ouvre la même interface. Casser une moitié retire tout le serveur et ne rend qu’un objet avec une pioche adaptée ; en créatif, aucun objet n’est lâché.

Après la mise à jour depuis le modèle d’un bloc, redémarrer Minecraft puis reposer les anciens serveurs et les associer au réseau existant. Un simple rechargement des textures ne suffit pas pour changer la logique de placement.

## Recettes

Les blocs se fabriquent dans une table de fabrication et produisent un bloc chacun. Ils utilisent les composants partagés de HomeCore : le **Circuit imprimé HomeLink** et le **Microprocesseur HomeLink**. Ces composants s’assemblent uniquement dans l’**Établi électronique HomeLink** de HomeCore, lui-même fabriqué avec du fer, du cuivre, de la redstone, une table de fabrication et des planches ; ils n’ont pas de recette en table de fabrication. Une recette se débloque dans le livre de recettes dès que le composant correspondant entre dans l’inventaire.

**HomeLink Server** : cinq lingots de fer, un microprocesseur, un circuit imprimé et deux poudres de redstone.

```text
Fer       Microprocesseur  Fer
Redstone  Circuit          Redstone
Fer       Fer              Fer
```

**HomeLink Dashboard** : cinq lingots de fer, trois blocs de verre et un circuit imprimé.

```text
Fer    Fer      Fer
Verre  Verre    Verre
Fer    Circuit  Fer
```

**Répéteur HomeLink** : quatre lingots de fer, un répéteur de redstone et un circuit imprimé.

```text
Fer    Vide       Fer
Vide   Répéteur   Vide
Fer    Circuit    Fer
```

Les définitions livrées sont [home_server.json](../src/main/resources/data/homelink_dashboard/recipe/home_server.json), [dashboard_display.json](../src/main/resources/data/homelink_dashboard/recipe/dashboard_display.json) et [signal_repeater.json](../src/main/resources/data/homelink_dashboard/recipe/signal_repeater.json). Utiliser une pioche adaptée pour récupérer les blocs. Casser un point d’accès ne supprime pas le HomeNetwork ; son association locale devra être choisie de nouveau après placement.

## HOME : favoris et widgets

HOME affiche le nom du réseau, les nombres d’appareils reçus et disponibles, leurs états et le nombre d’alertes retenues. Les favoris et widgets sont personnels, enregistrés **par joueur et par réseau**, côté serveur. Ils sont donc communs aux points d’accès que ce joueur utilise sur ce réseau.

Le bouton **Favoris** ouvre le sélecteur d’appareil. Cliquer sur son nom pour parcourir les appareils reçus, puis utiliser **Ajouter aux favoris** ou **Retirer des favoris**. Un Viewer peut gérer ses propres favoris.

Avec la permission CONFIGURE, **Disposition** ouvre l’éditeur :

- **Ajouter des widgets** : choisir l’appareil, puis éventuellement une métrique ; ajouter un résumé d’appareil ou un widget de métrique.
- **Placer les widgets** : sélectionner un widget, utiliser les quatre flèches, changer sa largeur avec **Taille 6 / 12**, ou le supprimer.
- **Terminer** : revenir à HOME. La molette fait défiler la grille lorsque nécessaire.

Cliquer directement sur un widget dans HOME ouvre son placement si vous êtes autorisé. Les widgets ajoutés occupent 6 × 3 cases ; le redimensionnement propose 12 × 3 et 6 × 3. La grille comporte 12 colonnes et 64 lignes. Les chevauchements et les sorties de grille sont refusés. Chaque modification attend la confirmation serveur avant d’afficher le nouvel état.

La limite est de 32 widgets et 64 favoris par profil. Les préférences stockent des références aux appareils et métriques, pas leurs valeurs. Si un appareil disparaît, le widget reste présent avec une indication d’indisponibilité ; il peut être supprimé dans l’éditeur.

## DEVICES : consulter et contrôler

La recherche filtre localement les données déjà reçues par nom, type ou capability. Elle n’envoie aucun paquet serveur à chaque caractère. Le bouton d’état parcourt ALL, ONLINE, WARNING, OFFLINE, ERROR et UNKNOWN ; les résultats sont triés par nom.

Cliquer sur un appareil ouvre son nom, type, état, position éventuelle et métriques. La molette fait défiler les valeurs. Pour les types inconnus, Dashboard conserve une présentation textuelle lorsque HomeCore fournit une valeur transportable.

Le bouton **Actions** ouvre les actions exposées par l’appareil. Les flèches parcourent les actions disponibles. Les contrôles suivent le schéma HomeCore : bouton, ON/OFF, entier, décimal, slider, choix déroulant, texte ou coordonnées XYZ. Le menu déroulant se parcourt à la souris, à la molette ou au clavier.

Modifier un champ ne déclenche pas l’action : cliquer sur **Exécuter**. Le serveur vérifie de nouveau les permissions, l’état de l’appareil et les paramètres. Les résultats possibles comprennent SUCCESS, DENIED, INVALID_PARAMETER, DEVICE_OFFLINE, RATE_LIMITED et FAILED. Une action n’est utilisable que sur un appareil ONLINE ; WARNING n’est pas un état autorisant son exécution dans HomeCore.

## ALERTS : événements reçus

ALERTS affiche les événements du réseau reçus pendant la session : heure locale, gravité, source, type et message. Le filtre propose ALL, INFO, WARNING et CRITICAL. Une infobulle donne le texte et des données supplémentaires, avec une taille limitée pour rester lisible.

Cliquer sur une alerte ouvre l’appareil source s’il figure toujours parmi les appareils reçus. Un appareil supprimé ou situé au-delà de la limite de suivi ne peut pas être ouvert depuis cette alerte.

L’historique est limité, en mémoire, et commence lorsque le Dashboard s’abonne. Ce n’est pas un journal persistant : fermer l’interface termine la session et libère les alertes. Les événements antérieurs à l’ouverture ne sont pas rejoués. HomeCore limite la livraison des événements à 16 par tick et 32 par seconde et par joueur ; un flux très intense peut donc être réduit.

## NETWORK et SETTINGS

NETWORK affiche le nom, la connexion CONNECTED / UNREACHABLE / OFFLINE, le propriétaire identifié par UUID, le nombre de membres, votre rôle et les appareils. **Avancé** affiche l’UUID du réseau. Cette page est informative : elle ne propose pas encore d’éditeur de membres, de changement de propriétaire ou de gestion radio.

SETTINGS contient uniquement des réglages client :

| Réglage | Défaut | Plage |
| --- | --- | --- |
| Alertes conservées pendant la session | 128 | 1 à 512 |
| Intervalle de mise à jour des vues en ticks | 2 | 1 à 20 |

Ces valeurs sont enregistrées dans `config/homelink_dashboard-client.toml`. L’intervalle des vues ne change ni les permissions ni la fréquence de publication des appareils HomeCore.

Les boutons parcourent des valeurs prédéfinies : 32, 64, 128, 256 et 512 alertes ; 1, 2, 4, 10 et 20 ticks. Le fichier de configuration accepte les plages complètes indiquées ci-dessus.

## Droits

La politique HomeCore de référence est la suivante :

| Rôle | Consultation | Favoris personnels | Actions | Widgets / association |
| --- | --- | --- | --- | --- |
| VIEWER | Oui | Oui | Non | Non |
| MEMBER | Oui | Oui | Selon la permission de l’action | Non |
| ADMIN / OWNER | Oui | Oui | Selon la validation serveur | Oui |

La configuration initiale d’un point d’accès non associé est réservée au joueur qui l’a posé. Dashboard ne propose pas d’interface d’invitation : les membres sont administrés via HomeCore ou une intégration autorisée. Les contrôles visuels désactivés ne remplacent jamais la validation serveur.

## Sauvegardes et limites

- Les associations et propriétaires des blocs sont sauvegardés avec les BlockEntities dans les chunks du monde.
- Les profils Dashboard sont dans `<monde>/data/homelink_dashboard_preferences.dat`.
- Les réseaux HomeCore sont dans `<monde>/data/homecore_networks.dat`.
- Les réglages visuels sont locaux au client ; les alertes sont temporaires.

La version 1.0 suit jusqu’à 128 appareils par réseau ouvert. Le surplus est signalé ; seuls les appareils synchronisés sont recherchables et consultables. HomeCore distribue les snapshots initiaux sur plusieurs ticks, avec un budget de 16 snapshots et 256 deltas métriques par tick et par observateur. Un grand réseau peut donc mettre un court délai à se remplir. Les métriques suivent ensuite leur politique HomeCore, sans renvoi complet périodique du réseau.

Un test en jeu a exercé 100 appareils avec huit métriques chacun. Les performances restent dépendantes du matériel, du serveur et des fournisseurs d’appareils ; ce test ne constitue pas une mesure de FPS.

## Dépannage

| Symptôme | Vérification ou action |
| --- | --- |
| Aucun appareil | Vérifier qu’un mod compatible enregistre ses appareils dans HomeCore et les associe à ce HomeNetwork. Dashboard seul ne crée aucun appareil métier. |
| Aucun réseau proposé au Display | Créer d’abord un réseau sur un HomeLink Server ; vérifier vos permissions VIEW et CONFIGURE et que vous possédez le point non associé. |
| Accès refusé | Vérifier le réseau, l’appartenance du joueur, la distance au bloc et son état actif. |
| Action désactivée | Vérifier le rôle, les permissions supplémentaires de l’action, l’état ONLINE et la disponibilité de son schéma. |
| INVALID_PARAMETER | Respecter les bornes, le pas, les choix proposés et la longueur de texte. Une validation métier supplémentaire peut exister côté appareil. |
| RATE_LIMITED | Attendre brièvement avant une nouvelle demande. |
| Synchronisation interrompue | Utiliser **Actualiser** ; si nécessaire fermer puis rouvrir l’interface après rétablissement de la connexion. |
| Widget d’appareil indisponible | L’appareil a été supprimé, déchargé ou n’est pas dans la sélection reçue. Vérifier le mod fournisseur et le plafond de suivi. |
| Ancien point lié à un réseau supprimé | Faire Maj + clic droit, main vide, pour réinitialiser l’association, puis choisir le réseau voulu. Le propriétaire doit avoir les droits de configuration si l’ancien réseau existe encore. |
| Préférences impossibles à charger | Consulter le journal serveur. Un fichier global illisible n’est pas remplacé automatiquement ; restaurer une sauvegarde valide du monde. |

Pour les commandes de test et les résultats réellement observés, consulter le [guide développeur](DEVELOPER_GUIDE.md) et les rapports [phase 7](PHASE_7.md) / [release](PHASE_8.md).

### Serveur à côté, mais écran ou répéteur sans signal

Plusieurs réseaux peuvent avoir le même nom. Le sélecteur les distingue par un identifiant court et propose les réseaux à portée en premier. Pour corriger un bloc déjà associé au mauvais réseau : **Maj + clic droit, main vide**, puis choisir **Signal disponible ici**. Cette opération conserve les réseaux et les appareils. Une association hors portée est désormais refusée avant enregistrement.

## Nommer et renommer un réseau

Le formulaire de création du serveur comporte un champ de nom. Saisir de 1 à 128 caractères puis créer le réseau ; les espaces en début et fin sont retirés. Les noms vides, caractères de contrôle et codes de couleur sont refusés.

Pour renommer : **Réseau → Renommer → Enregistrer**. Le propriétaire et les administrateurs disposant de **MANAGE_NETWORK** y ont accès. Le résultat apparaît dans le formulaire et le nouveau nom est synchronisé. L'identifiant, les appareils, les membres, favoris et dispositions ne changent pas. Le nom est conservé après redémarrage.

Le répéteur affiche désormais un petit voyant vert clignotant lorsqu'il est connecté ; hors connexion, il reste gris. L'animation est décorative et n'indique pas le débit du réseau.
