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
| Téléchargement | Onglet **Télécharger** : lien direct `.mp3`, ou catalogue libre Internet Archive (CC / domaine public), extrait avant téléchargement ; fichiers enregistrés dans `Musique/KHN MP3` |
| Paramètres du son | Égaliseur (bandes de l'appareil + 10 préréglages), basses, spatialisation 3D, amplification, vitesse et tonalité |
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

## Installer l'APK

1. Copier `KHN-MP3-v1.0.0.apk` sur le téléphone.
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
