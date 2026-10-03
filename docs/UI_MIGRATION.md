# Migration du GUI Dashboard / Dashboard UI migration

Le Dashboard conserve son langage visuel de référence et consomme désormais HomeCore 1.14.0, API publique 1.9.0. Les implémentations locales `DashboardTheme` et `DashboardButton` sont supprimées.

| Ancien appel / Previous call | API publique / Public API |
| --- | --- |
| `DashboardTheme.TEXT`, `ACCENT`, autres tokens | `HomeLinkTheme` |
| `DashboardTheme.status(value)` | `HomeLinkTheme.statusColor(value)` ; préférer `HomeLinkStatusTone` pour les nouveaux écrans |
| `DashboardTheme.frame`, `panel`, `screw`, `mark`, `input` | mêmes helpers dans `HomeLinkUi` |
| `DashboardButton.builder`, `selected`, `navigation` | `HomeLinkButton` |
| surface centrée 520 × 340, marge de 8 px | `HomeLinkScreenLayout.fit(width, height, 520, 340)` |

Les coordonnées fonctionnelles et les widgets spécifiques restent dans les vues Dashboard. Aucune logique de navigation, profil, radio, partage d'écran ou transport n'est déplacée dans HomeCore. Les appels de rendu conservent la palette, les reliefs, l'onglet actif, le focus ambre et les champs de la référence Dashboard.

Les contrôles d'actions utilisent désormais les tokens et contrôles HomeCore : boutons booléens sélectionnés, focus ambre, champs stylisés, panneau de liste et scrollbar cuivre du menu déroulant. Les hauteurs fonctionnelles de 20 pixels du menu déroulant et du slider sont conservées. Les libellés tronqués du menu déroulant affichent une ellipsis et un tooltip complet. Le chrome et le bouton de référence restent équivalents au Dashboard historique ; les anciens contrôles d'actions recevant cette normalisation ne sont pas présentés comme identiques pixel par pixel.

**Action controls:** boolean buttons, styled fields and dropdown rendering now consume HomeCore. The dropdown and slider retain their functional 20-pixel height. The dropdown uses an amber keyboard focus, copper scrollbar, ellipsis and full-label tooltips. The reference frame and button appearance are preserved; these normalized action widgets have intentional visual changes.

**English:** the public kit lives in `fr.lkdm.homecore.api.client.ui`. Import it exclusively from client screens, widgets and renderers. It requires no event-bus registration and contains no Dashboard dependency. New consumers such as HomeLink Furnace can create their screen using `HomeLinkUi.frame(...)`, `panel(...)`, `HomeLinkButton.builder(...)`, `HomeLinkTheme.CONTROL_HEIGHT` and `HomeLinkScreenLayout.fit(...)` without installing Dashboard. Preserve native Minecraft narration, keyboard traversal, translated labels and semantic status text. Keep consumer-specific state and actions in that mod.

Les dépendances Gradle et les métadonnées doivent déclarer explicitement HomeCore `1.14.0` / `[1.14.0,2.0.0)`. Le composite local vérifie cette version ; utiliser l'API n'effectue aucune mise à jour automatique et n'embarque pas HomeCore dans le JAR consommateur.

La validation exécutée doit être rapportée séparément des règles de migration ; ce document ne constitue pas un résultat de test.
