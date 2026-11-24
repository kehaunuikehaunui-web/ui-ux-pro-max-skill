# KHN MP3

Lecteur de musique Android personnel : Kotlin, Jetpack Compose et Media3/ExoPlayer, thème sombre néon.

## Fonctions

| Besoin | Où dans l'app |
|---|---|
| Écouter sa musique | Onglet **Musique** : tous les MP3/M4A/FLAC/OGG du téléphone (MediaStore) |
| Lecture en arrière-plan | Service Media3 : notification, écran de verrouillage, casque Bluetooth ; la musique continue quand l'app est fermée |
| Favoris | ♥ sur chaque titre ; filtre **Favoris** et playlist « Favoris » |
| Playlists | Onglet **Playlists** : créer, renommer, supprimer, ajouter plusieurs titres, réordonner |
| Recherche | Onglet **Recherche** : titre, artiste, album, playlist (accents ignorés) |
| Téléchargement | Onglet **Télécharger** : lien direct `.mp3` ; catalogues libres **Internet Archive**, **ccMixter** et **Jamendo** (clé gratuite à saisir) avec extrait avant téléchargement ; fichiers enregistrés dans `Musique/KHN MP3` |
| Bibliothèque audio YouTube | Bouton dans **Télécharger** : ouvre la bibliothèque de musique gratuite de YouTube dans le navigateur ; les fichiers téléchargés apparaissent dans le filtre **Téléchargés**. Les liens de vidéos YouTube sont refusés (conditions d'utilisation de YouTube et droits d'auteur). |
| Supprimer / Corbeille | Appui long sur un titre → sélection multiple → 🗑, ou menu ⋮ › **Mettre à la corbeille**. **Corbeille** (icône 🗑 en haut de Musique, ou Réglages › Bibliothèque) : restaurer ou supprimer définitivement, conservation 30 jours. Android 11+ : corbeille d'Android (fichier restauré au même endroit) ; Android 8–10 : copie dans l'app, restaurée dans `Musique/KHN MP3`. Favoris et playlists rétablis à la restauration |
| Partager vers KHN MP3 | Depuis WhatsApp, Telegram, Drive, e-mail… : un fichier audio partagé est copié dans `Musique/KHN MP3` ; un lien partagé pré-remplit le champ de téléchargement |
| Paramètres du son | Égaliseur (bandes de l'appareil + 10 préréglages), basses, spatialisation 3D, vitesse et tonalité |
| Augmenter le son | Amplification jusqu'à +15 dB, bouton **Amplifier** dans le lecteur |
| Qualité sonore | **Anti-saturation** : limiteur en fin de chaîne (Android 9+), le son reste propre même amplifié |
| Pendant les appels | La musique continue à volume réduit (réglable) pendant les appels vidéo/vocaux, retour au volume normal à la fin ; désactivable dans **Paramètres du son** |
| Égaliseur personnalisable | Préréglages perso enregistrés sous un nom (★), appliqués sur n'importe quel appareil (adaptation aux bandes) |
| Paroles synchronisées | Bouton **Paroles** du lecteur : karaoké (ligne en cours centrée, toucher pour y aller). Sources : paroles intégrées au MP3 (ID3 USLT), fichier `.lrc` (importé ou à côté du fichier), sinon **LRCLIB** en ligne ; mises en cache pour le hors-ligne |
| Pochettes | Image de la galerie pour un titre ou tout un album, pochette de playlist ; style **carré / arrondi / vinyle** qui tourne ; pochette perso aussi dans la notification |
| File intelligente | Aléatoire intelligent (évite titres récents et même artiste à la suite), lecture automatique de titres proches en fin de file, glisser-déposer dans la file, reprise de la file et de la position au redémarrage |
| Transitions | Fondu entre les morceaux 0–10 s, fondu court à la pause/reprise |
| Lecteur | Couleurs tirées de la pochette, glisser la pochette pour changer de titre, minuterie de mise en veille (durée ou fin du morceau) |
| Photo d'arrière-plan | **Réglages › Apparence** : photo de la galerie, assombrissement, flou (Android 12+) |
| Fichiers personnels | Bouton 📂 : importer des fichiers depuis n'importe quel dossier (carte SD, Téléchargements…) ; « Ouvrir avec KHN MP3 » depuis le gestionnaire de fichiers |

Cible : Android 8.0+ (API 26), affichage portrait adaptatif conçu pour 1080×2400.

## Fluidité

- Version release optimisée par R8 (code + ressources réduits), APK d'environ 3 Mo.
- `profileinstaller` : les profils de démarrage de Compose sont compilés à l'installation, même sans passer par le Play Store.
- Position de lecture lue dans la phase de dessin (barre du mini-lecteur) : pas de recomposition 4 fois par seconde.
- Listes `LazyColumn` avec clés stables, modèles `@Immutable`, pochettes chargées à la taille d'affichage avec cache mémoire.
- Analyse de la bibliothèque, recherche et filtrage hors du thread UI ; rescan automatique (anti-rebond 1,5 s) quand un fichier est ajouté.
- Égaliseur en aperçu direct pendant le glissement, sauvegardé seulement au relâchement.

## Son : chaîne d'effets

`Égaliseur → Basses → Spatialisation → Amplification + Limiteur` (DynamicsProcessing : seuil -1 dBFS, ratio 10:1).
Sur Android 8, l'amplification passe par `LoudnessEnhancer` (pas de limiteur dédié).

Pendant un appel, seul l'utilisateur entend la musique : Android ne permet pas à une app d'injecter du son dans un appel.

## Installer l'APK

1. Copier `KHN-MP3-v1.5.0.apk` sur le téléphone (une nouvelle version s'installe par-dessus l'ancienne, sans perte de données).
2. L'ouvrir, puis autoriser « Installer des applis inconnues » pour l'app qui l'ouvre (Fichiers, Chrome…).
3. Au premier lancement, autoriser l'accès à la musique.

Les mises à jour doivent être signées avec **la même clé** (`khn-mp3-release.jks`). Garde-la précieusement : sans elle, il faudra désinstaller l'app, ce qui efface les favoris et les playlists.

## Compiler

Prérequis : JDK 17+ et SDK Android 35.

```bash
# keystore.properties (non versionné), à la racine de projects/khn-mp3 :
#   storeFile=/chemin/khn-mp3-release.jks
#   storePassword=...
#   keyAlias=khnmp3
#   keyPassword=...
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Sans `keystore.properties`, l'APK release est signée avec la clé de debug.

## Structure

```
app/src/main/java/com/khn/mp3/
├── KhnApp.kt, MainActivity.kt   # Application, injection manuelle, intents « Ouvrir avec »
├── data/                        # MediaStore, favoris/playlists (JSON), réglages (DataStore), Internet Archive, téléchargements
├── playback/                    # PlaybackService (Media3), PlayerController, AudioEffects (égaliseur)
└── ui/                          # Compose : thème néon, composants, écrans
```
