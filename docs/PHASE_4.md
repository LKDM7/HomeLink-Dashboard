# Phase 4 — Device Control

Les Actions sont décodées depuis le schéma public HomeCore et affichées par
ActionControlRegistry : bouton, booléen, entier/double, slider, choix, texte et position XYZ.
La soumission appelle HomeCoreClient.executeAction ; Dashboard ne modifie aucune Metric
optimistement et n'exécute aucun handler. Les résultats sont corrélés, bornés et affichés.
La GUI reflète les permissions connues ; HomeCore valide toujours les droits et valeurs.

Validation réelle : build et 18 GameTests réussis ; runActionSmoke a exécuté les 8 types
dans un client Minecraft, vérifié les paramètres reçus par les handlers serveur, puis
les deltas. Des requêtes volontairement invalides ont obtenu INVALID_PARAMETER,
DEVICE_OFFLINE, DENIED et RATE_LIMITED. Les SUCCESS passent par l'état de la GUI.
La capture `build/validation/actions/screenshots/homelink-action-smoke.png` a été inspectée.
Journaux : `build/phase4-server-command.log`, `build/phase4-client-command.log`.

Créés : `client/state/DeviceActionView.java`, `client/widget/ActionControlRegistry.java`,
`client/widget/ActionPanel.java`, `verification/ActionClientSmoke.java`,
`verification/ActionStateGameTests.java`, ce rapport.
Modifiés : DashboardClientState, DebugDeviceView, DashboardScreen, build.gradle, EN/FR.
Chemins client relatifs à `src/main/java/fr/lkdm/homelink/dashboard/` ; vérifications
sous `src/verification/java/fr/lkdm/homelink/dashboard/verification/`.

Limites temporaires : SELECT cyclique, à remplacer par un menu déroulant en finition.
Les changements de rôle sont contrôlés immédiatement par HomeCore côté serveur,
mais les indications GUI utilisent le dernier snapshot de rôle connu.
Les valeurs de slider ne disposant pas d'une étendue double finie sont désactivées.
Les contrôles n'inventent pas de valeur courante lorsque le schéma d'action n'en expose pas.
