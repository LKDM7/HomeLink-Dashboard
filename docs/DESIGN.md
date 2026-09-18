# Interface et écran mural

Le Dashboard suit une direction de **terminal industriel**, choisie par l'utilisateur : gris neutres, boutons physiques, panneaux encastrés et affichage compact. Un ambre discret signale la sélection ; les voyants vert, ambre et rouge indiquent les états. Le rendu utilise les primitives GUI et la police de Minecraft, sans animation permanente ni bibliothèque supplémentaire.

Référence : les [terminaux Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/guidebook/items-blocks-machines/terminals.md), pour leur densité et leurs commandes de machine. Les dessins, ressources et textures du mod ne sont pas copiés ; le cadre et les commandes HomeLink sont dessinés en code natif.

## Interface

- `DashboardTheme` centralise les surfaces et couleurs. Le cadre comporte un biseau et quatre petites vis. Le bandeau contient le titre et un voyant de connexion, sans répéter le logo.
- Les onglets sont des boutons en relief ; celui de la page active apparaît enfoncé et possède un petit témoin. Le focus clavier conserve un contour visible. Une infobulle conserve le libellé complet quand l'espace est réduit.
- La fenêtre peut atteindre 520 × 340 unités GUI. Elle se réduit à la taille disponible avec une marge extérieure.
- HOME présente le réseau et les favoris. Lorsque la hauteur suffit, les quatre compteurs sont réunis dans une seule ligne. Les favoris occupent des lignes de 48 unités GUI et conservent leurs deux premières mesures ; le layout enregistré des widgets reste intact.
- Les appareils occupent des lignes de 44 unités GUI, avec un pictogramme générique, le type, l'état et deux mesures côte à côte. Les listes restent virtualisées et la recherche reste locale.
- Les boutons, barres de progression et panneaux partagent le même vocabulaire visuel. Les valeurs, permissions et messages proviennent toujours du système existant.

## Bloc écran

L'écran reste un bloc unique. Sa façade est rectangulaire, avec cadre graphite, support arrière et relief peu profond. Les quatre orientations possèdent une forme de collision adaptée. Les modèles Online, Offline et Error héritent de la même géométrie.

La représentation miniature sur le bloc est décorative : elle ne prétend pas afficher les appareils ou leurs valeurs. Son voyant reflète l'état réel du point d'accès ; le panneau s'éteint hors ligne. Le clic ouvre le véritable Dashboard.

## Vérification

Les scénarios de vérification du projet couvrent les blocs et leurs modèles, l'ouverture, les favoris et layouts, la recherche et les Metrics, les huit types d'actions, les événements et le réseau, ainsi que 100 appareils et 800 Metrics dans plusieurs tailles de fenêtre. Les captures et résultats de cette révision sont référencés dans `PHASE_8.md`.
