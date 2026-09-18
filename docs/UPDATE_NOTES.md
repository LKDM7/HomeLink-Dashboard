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
