# NPS Authentificateur

Application Android **native (Kotlin + Jetpack Compose)** de codes 2FA (TOTP / HOTP), **hors ligne par défaut**.

## Fonctions
- Codes TOTP (RFC 6238) et HOTP (RFC 4226) : SHA1 / SHA256 / SHA512, 6 à 8 chiffres, période configurable
- Ajout par **scan QR** (CameraX + ML Kit embarqué, fonctionne sans réseau), depuis une **image** de la galerie, ou **saisie manuelle**
- Import direct des QR d'export de **Google Authenticator** (`otpauth-migration://`)
- Onglet **QR** : les QR scannés qui ne sont pas du 2FA (liens, texte…) sont rangés dans un historique chiffré (Copier, Partager, Supprimer, Ouvrir)
- Ré-affichage du QR d'un compte (Partager / Enregistrer)
- Icônes des services : première lettre hors ligne, icône téléchargée puis gardée en local dès que Internet revient
- Recherche, groupes, renommage, copie en un tap
- Corbeille : comptes supprimés conservés 90 jours
- Sauvegarde : export/import **chiffré par mot de passe** (PBKDF2-SHA256 + AES-256-GCM), fichier `.npsbak`
- Verrouillage biométrique / code de l'appareil, blocage des captures d'écran
- Thème sombre uniquement

## Sécurité
- Permission `INTERNET` utilisée **uniquement** pour télécharger l'icône d'un service depuis son propre site (réglage désactivable). Jamais de clé, de code ni de nom de compte envoyés.
- ⚠️ ML Kit (lecture des QR) est une bibliothèque Google : avec la permission Internet présente, elle peut envoyer des statistiques d'usage à Google. Pour l'éviter, remplacer ML Kit par ZXing.
- Comptes stockés dans un fichier chiffré AES-256-GCM, clé non exportable dans l'Android Keystore
- `allowBackup="false"` : rien n'est copié dans les sauvegardes Android/cloud
- ⚠️ Si vous désinstallez l'app ou perdez la clé Keystore, les comptes sont irrécupérables : **faites des sauvegardes exportées**

## Compiler
### Avec GitHub Actions (recommandé)
1. Poussez ce dossier tel quel à la racine d'un dépôt GitHub.
2. Onglet **Actions** → workflow **Build APK** → téléchargez l'artefact `NPS-Authentificateur-apk`.
3. Un tag `v1.0.0` publie aussi l'APK dans les Releases.

### En local
Android Studio (JDK 17) : ouvrir le dossier, laisser Gradle synchroniser, puis *Run*.
En ligne de commande avec Gradle 8.9 installé : `gradle assembleDebug`
(le `gradle-wrapper.jar` n'est pas inclus ; générez-le une fois avec `gradle wrapper --gradle-version 8.9`).

## Signature
L'APK `release` est signé avec la clé **debug** pour rester installable sans keystore.
Pour une vraie publication, ajoutez votre propre `signingConfig` dans `app/build.gradle.kts`.

## Structure
```
app/src/main/java/com/nps/authenticator/
  MainActivity.kt          verrouillage + navigation
  otp/                     Base32, HOTP/TOTP, lecture otpauth:// et migration Google
  data/                    Account, stockage chiffré (Keystore), sauvegarde chiffrée
  ui/                      écrans Compose (liste, détail, ajout, sauvegarde, corbeille, réglages)
app/src/test/              vecteurs de test RFC 6238 / RFC 4226
.github/workflows/build.yml
```
