# Portée et répéteurs

Le HomeLink Server et chaque répéteur connecté couvrent une sphère de 64 blocs (distance entre positions de blocs, hauteur comprise, limite incluse). Les liaisons restent dans une dimension et ignorent les obstacles. Les relais doivent appartenir au même HomeNetwork. Le Dashboard Display reçoit le signal sans le retransmettre.

Un répéteur se configure par clic droit, avec les mêmes vérifications de propriétaire et de permission CONFIGURE que l’écran. Il peut associer un réseau existant mais ne peut pas en créer. Voyant vert clignotant : connecté ; gris : sans signal ; rouge : réseau supprimé.

Le sélecteur propose d’abord les réseaux dont le signal atteint le bloc. Il affiche un identifiant court pour distinguer les réseaux portant le même nom et une indication de signal. L’écran et le répéteur ne peuvent pas être associés hors portée ; le serveur revalide la liaison avant l’enregistrement.

Pour corriger une ancienne association : **Maj + clic droit avec la main vide** sur l’écran ou le répéteur, puis choisir le réseau marqué **Signal disponible ici**. Le propriétaire physique peut réinitialiser l’association même sans signal, à condition de disposer du droit CONFIGURE sur le réseau encore existant. Cela ne supprime ni les réseaux ni leurs appareils. Un réseau supprimé peut également être détaché par le propriétaire du bloc.

Seuls les émetteurs chargés et actifs sont considérés. Une chaîne ou une boucle de répéteurs sans serveur ne fonctionne pas. Aucun chunk n’est chargé artificiellement. La destruction, désactivation et le déchargement invalident le graphe. Les appareils hors portée quittent la liste au prochain contrôle du registre (environ une seconde), reviennent automatiquement lorsque la liaison est restaurée, et conservent leurs favoris et widgets.

## Intégration HomeCore

Installer **HomeCore 1.3.0** avec le nouveau JAR Dashboard sur le client et le serveur. L’API publique ajoute des contraintes de liaison identifiées par ResourceLocation à HomeNetworkManager. Leur décision est appliquée côté HomeCore aux requêtes de listes, snapshots, deltas, événements et actions. Les permissions restent obligatoires. Les politiques défaillantes refusent l’accès. Le protocole réseau reste inchangé (2).

Dashboard ne modifie ni les métriques ni l’appartenance des appareils. Il ne détecte pas les blocs voisins : l’intégration HomeCore de chaque mod fournit toujours ses appareils et leur réseau. Un appareil physique doit fournir sa position et sa dimension. Un appareil sans aucune localisation reste logique et exige un serveur chargé ; une localisation partielle est refusée.

Les réseaux physiques sont marqués dans `homelink_dashboard_radio.dat`, afin que retirer tous les émetteurs ou redémarrer ne rétablisse pas une portée illimitée. Les réseaux HomeCore jamais associés à un bloc HomeLink conservent leur comportement logique. Les anciens réseaux deviennent physiques au chargement d’un de leurs points HomeLink.

## Vérifications

- HomeCore : `build` réussi, 88 tests unitaires, dont refus immédiat des actions, erreur de politique, listes classiques et flux snapshot/delta/événements.
- Dashboard : GameTests de distance 64/65, diagonale 3D, chaîne de deux relais jusqu’à 192 blocs, autres dimensions et réseaux, désactivation, destruction, déchargement/rechargement, persistance de l’association et du marqueur réseau.
- Recette, livre de recettes, pioche et butin du répéteur contrôlés avec les ressources réellement chargées.
- Client Minecraft : modèles de tous les états, ouverture et association du répéteur ; appareil à 65 blocs retiré, retrouvé après ajout d’un relais, puis retiré après sa destruction, sans nouvelle souscription.
- Charge existante : 100 appareils, 800 mesures, recherche locale, deltas et nettoyage des listeners vérifiés dans le client.

Journaux : `build/radio-client-validation.log`, `build/radio-final-validation.log`, `build/radio-release-validation.log` ; tests HomeCore dans `../HomeCore/build/radio-validation.log`.
