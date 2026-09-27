# Version 1.3.0 — Machines, ajout de machines et écran en trois tailles

Cette mise à jour nécessite **HomeCore 1.9.0** (API 1.5.0) sur le client et le serveur. La plage déclarée devient `[1.9.0,2.0.0)`. HomeLink Energy reste obligatoire ; la version 0.2.0 est conseillée.

- **Machines** : nouvel onglet qui résume les machines par système (Énergie, Ferme, Carrière ; Stockage seulement s'il est sur le réseau) : production, stockage, cultures, progression des carrières, remplissage. Cliquer sur une machine ouvre sa fiche.
- **Ajouter** : nouvel onglet qui liste les machines dans la zone radio du serveur et des répéteurs, avec leur nom (y compris un nom d'enclume), leur type, leur distance et leur réseau, puis les ajoute ou les déplace sur ce réseau. Requiert MANAGE_NETWORK et les droits sur la machine. Machines compatibles : HomeLink Energy 0.2.0, Farm 1.1.0 et Quarry 1.1.0.
- **Écran en trois tailles** : 1 × 1, 2 × 1 ou 2 × 2, choisi à la pose comme un tableau ; Maj + clic force le 1 × 1. Casser une case retire tout l'écran. Un câble d'énergie peut alimenter n'importe quelle case. Les écrans existants restent en 1 × 1.
- **Façade en direct** : l'écran affiche vos favoris (1, 2 ou 4 selon la taille), selon vos propres droits.
- **Noms de réseau** : la création propose un nom libre (« HomeLink · Joueur 2 »…) et signale un nom déjà utilisé par un autre de vos réseaux.

Vérifications du 27 septembre 2026 : `build`, **75 GameTests serveur** et le test client connecté réussis avec HomeCore 1.9.0 et HomeLink Energy 0.2.0. Le test client pose un panneau solaire réel, l'ajoute depuis l'onglet Ajouter et le retrouve dans Machines → Énergie.

# Version 1.2.0 — HomeCore 1.7.0 et module de communication

Cette mise à jour nécessite **HomeCore 1.7.0** sur le client et le serveur. La plage déclarée devient `[1.7.0,2.0.0)`.

- Le **module de communication HomeLink** (`homecore:homelink_communication_module`), assemblé dans l’établi électronique, équipe désormais les appareils radio :
  - **HomeLink Server** : le module de communication remplace le circuit imprimé ; le microprocesseur reste.
  - **Répéteur HomeLink** : le module de communication remplace le circuit imprimé.
  - **HomeLink Dashboard** : inchangé, avec un circuit imprimé.
- Les recettes du serveur et du répéteur se débloquent à l’obtention du module de communication.
- Les recettes 1.1.0 à circuit imprimé du serveur et du répéteur ne fonctionnent plus. Les blocs déjà posés et les réseaux existants ne changent pas.
- Le chapitre « Premiers pas » du manuel intégré est mis à jour.

Vérifications du 26 septembre 2026 : `build` et contrôle du JAR `homelink_dashboard-1.2.0.jar` réussis, **44 GameTests serveur** passés avec HomeCore 1.7.0. Les tests vérifient les nouvelles recettes et refusent les variantes du serveur et du répéteur à circuit imprimé.

# Version 1.1.0 — HomeCore 1.6.1, recettes à composants et interface HomeLink Storage

Cette mise à jour nécessite **HomeCore 1.6.1** sur le client et le serveur. La plage déclarée devient `[1.6.1,2.0.0)`.

- Les recettes utilisent les composants partagés de HomeCore, assemblés dans l’**Établi électronique HomeLink** :
  - **HomeLink Server** : le microprocesseur remplace le quartz et le circuit imprimé remplace le comparateur.
  - **HomeLink Dashboard** et **Répéteur HomeLink** : le circuit imprimé remplace la poudre de redstone.
- Les recettes se débloquent dans le livre de recettes à l’obtention du composant (microprocesseur pour le serveur, circuit imprimé pour l’écran et le répéteur).
- Le chapitre « Premiers pas » du manuel intégré présente ces composants et l’établi.
- Les anciennes recettes ne fonctionnent plus. Les blocs déjà posés et les réseaux existants ne changent pas.

Vérifications du 26 septembre 2026 : compilation et contrôle du JAR de release `homelink_dashboard-1.1.0.jar` réussis, **44 GameTests serveur** passés. Les tests couvrent les trois nouvelles recettes avec les composants HomeCore chargés et vérifient que l’ancienne recette du répéteur est refusée. Journal local : `build/homecore-161-validation.log`.

## Interface alignée sur HomeLink Storage

- Cadre commun (`DashboardTheme.frame`), avec le filet de pied au-dessus de la rangée de boutons.
- Boutons et champs de saisie de 18 pixels ; chaque bouton affiche son libellé en info-bulle ; les champs utilisent les couleurs du thème.
- Le bouton **?** passe dans l’en-tête, en haut à droite, et reste enfoncé tant que le manuel est ouvert ; le voyant et l’état de connexion se placent juste à sa gauche.
- Manuel : chapitres bornés, boutons Haut/Bas désactivés en butée, raccourcis Page préc./suiv., Début/Fin et ←/→.

Vérifications de l’interface : 44 GameTests serveur, puis scénarios client connexion, manuel (ouverture par l’en-tête, 5 chapitres, défilement, Échap), actions, préférences, alertes et création/réparation de réseaux, tous réussis ; captures relues.

# Manuel, réseau radio et noms personnalisés

Cette mise à jour nécessite **HomeCore 1.3.0** sur le client et le serveur.

- Manuel intégré bilingue accessible par **?**, avec cinq chapitres, étapes en couleur et défilement.
- Portée de 64 blocs et répéteur plaçable : liaison au même réseau, chaînes, absence de signal et reconnexion automatique.
- Petit voyant vert animé sur le répéteur connecté ; voyant gris statique hors connexion.
- Sélecteur de réseaux distinguant les noms identiques par un identifiant court et proposant les réseaux à portée en premier.
- Réassociation par **Maj + clic droit, main vide**, sans suppression du réseau.
- Nom personnalisable à la création ; **Réseau → Renommer → Enregistrer** avec le droit MANAGE_NETWORK.

## Vérifications du 19 septembre 2026

Dashboard : compilation réussie et **43 GameTests serveur** passés, dont contrôles de session, permissions de renommage, noms invalides, limite de requêtes, recette du répéteur et régression des réseaux portant le même nom.

HomeCore : compilation complète réussie et **89 tests unitaires** passés. Le test de persistance sauvegarde et recharge réellement les nouveaux noms sur disque. Le renommage conserve l’UUID, les appareils, les membres et la date de création.

Client Minecraft : création depuis le champ de nom, renommage depuis le formulaire, résultat et nouveau nom synchronisés, accents ; sélection et réparation parmi cinq réseaux de même nom ; modèle connecté utilisant huit images d’animation pour son voyant, modèle hors ligne sans animation verte. La connexion générique et la perte/reprise de portée passent également.

Les vérifications antérieures sous charge ont passé 100 appareils et 800 mesures avec mises à jour différentielles. Aucun appareil spécifique à un mod tiers n’est nécessaire à ces fonctionnalités.

Journaux locaux : `build/network-names-server-tests.log`, `build/network-names-client-tests.log`, `build/pre-push-final-build.log` et `../HomeCore/build/network-names-validation.log`. Les fichiers de tests et de développement sont exclus des JAR de production.
