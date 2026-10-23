# ScanClient

App Android qui scanne (OCR hors ligne) les références client `<préfixe magasin><n° client>` sur les courriers retournés.

## Fonctionnement
- **Magasins** : liste de préfixes configurable (écran « Magasins »). Seuls ces préfixes + des chiffres sont enregistrés. Le n° client fait 6 ou 7 chiffres par défaut, réglable par magasin.
- **Tolérance OCR** : O/0, I/1, S/5, B/8, Z/2… et LYON06 lu « LYONDB » sont corrigés. Le code enregistré est toujours celui de la liste.
- **Magasin inconnu** : message d'info pendant le scan (sans bloquer), avec bouton « Ajouter » pour l'ajouter à la volée.
- **Sessions** : « Nouvelle session » → scan → « Terminer ». Les sessions passées se retrouvent sur l'accueil : export CSV, renommage, reprise, suppression.
- Export CSV `;` UTF-8 : `session;horodatage;magasin;client;code` (Excel supprime les zéros de tête de la colonne client ; la colonne code est sûre).

## Compilation (GitHub Actions)
1. Lancer une fois le workflow **keystore** (génère `app/keystore/scanclient.jks`, à committer) : les mises à jour s'installent ensuite par-dessus l'ancienne version.
2. Chaque push sur `main` lance **build** → APK dans les artefacts.

## Tests
`./gradlew test` (nécessite Gradle ≥ 8.9) : `CodeParserTest`.
