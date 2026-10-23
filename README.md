# ScanClient

Scanne en continu (OCR ML Kit, hors ligne) les codes client `<magasin><n° client>` sur des documents, et les enregistre.

## Lancer
1. Android Studio (Ladybug ou plus récent) → *Open* sur ce dossier, laisser Gradle synchroniser
   (si besoin, accepter la mise à jour des versions proposée par l'IDE).
2. Brancher le téléphone (débogage USB) → *Run*.
3. Tests du parseur : `./gradlew test` ou clic droit sur `CodeParserTest`.

## Utilisation
- Passer la caméra devant le document : le code **le plus proche du centre** est lu, validé après 2 images identiques,
  puis bip + vibration + bandeau vert. Pas de bouton.
- Bandeau orange = déjà scanné (non enregistré en double).
- Toucher une ligne = corriger magasin / n° client. Glisser vers la gauche = supprimer (annulable).
- *Exporter CSV* : partage un fichier `;` UTF-8 (colonnes horodatage, magasin, client, code).
  Excel supprime les zéros de tête de la colonne `client` ; la colonne `code` est sûre.

## Apprentissage des magasins
La liste des magasins connus = les magasins distincts présents dans les scans. Le premier scan d'un magasin est découpé par
heuristique (n° client = 7 derniers chiffres, ou toute la fin numérique si plus courte) ; si le découpage est faux,
corrige-le une fois : les scans suivants l'utiliseront (et les erreurs OCR 0/O, 1/I, 5/S, 8/B du préfixe sont corrigées).

## Réglages
`CodeParser.kt` : longueurs min/max, longueur max du n° client. `CodeAnalyzer.REQUIRED` : nb d'images identiques avant validation.
