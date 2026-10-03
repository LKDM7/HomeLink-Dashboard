# Interface et écran mural

Le Dashboard suit une direction de **terminal industriel**, choisie par l'utilisateur : gris neutres, boutons physiques, panneaux encastrés et affichage compact. Un ambre discret signale la sélection ; les voyants vert, ambre et rouge indiquent les états. Le rendu utilise les primitives GUI et la police de Minecraft, sans animation permanente ni bibliothèque supplémentaire.

Référence : les [terminaux Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/guidebook/items-blocks-machines/terminals.md), pour leur densité et leurs commandes de machine. Les dessins, ressources et textures du mod ne sont pas copiés ; le cadre et les commandes HomeLink sont dessinés en code natif.

## Interface

- `HomeLinkTheme` et `HomeLinkUi`, fournis par HomeCore 1.14.0, centralisent les surfaces, couleurs et primitives partagées. Le cadre comporte un biseau et quatre petites vis. Le bandeau contient le titre et un voyant de connexion, sans répéter le logo. Les contrôles utilisent `HomeLinkButton` ; aucun thème Dashboard local n'est nécessaire.
- Les onglets sont des boutons en relief ; celui de la page active apparaît enfoncé et possède un petit témoin. Le focus clavier conserve un contour visible. Une infobulle conserve le libellé complet quand l'espace est réduit.
- La fenêtre peut atteindre 520 × 340 unités GUI. Elle se réduit à la taille disponible avec une marge extérieure.
- HOME présente le réseau et les favoris. Lorsque la hauteur suffit, les quatre compteurs sont réunis dans une seule ligne. Les favoris occupent des lignes de 48 unités GUI et conservent leurs deux premières mesures ; le layout enregistré des widgets reste intact.
- L'éditeur de HOME place la liste des appareils à gauche et un aperçu de l'accueil à droite. Le glisser-déposer ajoute et réordonne ; la disposition est recalculée dans l'ordre de lecture, sans placement case par case. La barre du bas règle le widget sélectionné.
- Les appareils occupent des lignes de 44 unités GUI, avec un pictogramme générique, le type, l'état et deux mesures côte à côte. Les listes restent virtualisées et la recherche reste locale.
- Les boutons, barres de progression et panneaux partagent le même vocabulaire visuel. Les valeurs, permissions et messages proviennent toujours du système existant.

## Bloc écran

L'écran est un panneau mural multibloc de 1 × 1, 2 × 1 ou 2 × 2 cases. Comme un tableau, sa pose choisit la plus grande taille disponible sur la face cliquée, en préférant la droite et le haut vus de face ; Maj + clic force le 1 × 1. Aucun mur plein derrière le panneau n'est requis. Un seul objet place l'ensemble, et les anciens écrans restent des maîtres 1 × 1 par défaut.

La case en bas à gauche vue de face est le maître : elle seule conserve le réseau, le propriétaire et l'énergie. Les autres cases relaient le clic et recopient son état. La consommation et l'interface sont identiques pour les trois tailles. Casser une case retire tout l'écran, avec un seul objet rendu si l'outil convient, aucun en créatif.

La façade rectangulaire conserve son cadre graphite, son support arrière et son relief peu profond. Le cadre est continu sur toute la surface, sans joint entre les cases. Les modèles sont découpés dans un dessin commun à chaque taille : sept pièces parents au total, chacune avec ses variantes Online, Offline et Error par textures. Les quatre orientations possèdent une collision fine et continue, sans trou à la jonction des rangées ; le 1 × 1 conserve sa forme initiale. Le modèle d'objet reste le 1 × 1.

La façade reprend les widgets de l'accueil du joueur dans leur disposition enregistrée : les 12 colonnes couvrent la largeur utile et une rangée de grille mesure 12 pixels, si bien qu'un widget 6 × 3 occupe une demi-largeur et affiche un nom et deux valeurs. Les widgets hors de la surface sont comptés en pied d'écran. Sans widget, elle affiche les favoris personnels et leurs deux premières mesures : un favori en 1 × 1, deux en 2 × 1 et quatre en 2 × 2 ; un indicateur signale les favoris accessibles supplémentaires. Les noms sont rognés à la place disponible. Le texte utilise la police Minecraft et la palette du terminal, sur une surface lisible indépendante de la lumière ambiante. Le cadre et le voyant restent les modèles existants ; le modèle d'objet conserve son dessin décoratif.

Le maître envoie un résumé autorisé par joueur toutes les secondes, dans un rayon de 16 blocs. Les mesures proviennent de HomeCore et respectent les permissions VIEW ainsi que la portée radio. Un joueur voit ses propres favoris, pas ceux du propriétaire du panneau. L'écran indique les états non associé, hors ligne, accès restreint ou aucun favori accessible. Le cache client expire et se vide à la déconnexion ; aucune valeur de façade n'est sauvegardée dans le bloc. Le clic ouvre toujours le Dashboard complet.

## Vérification

Les scénarios de vérification du projet couvrent les blocs et leurs modèles, l'ouverture, les favoris et layouts, la recherche et les Metrics, les huit types d'actions, les événements et le réseau, ainsi que 100 appareils et 800 Metrics dans plusieurs tailles de fenêtre. Les captures et résultats de cette révision sont référencés dans `PHASE_8.md`.
