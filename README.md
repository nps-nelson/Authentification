# Authentification Android

Application Android native en **Kotlin** avec une interface XML légère (le HTML n’est pas utilisé comme cœur du projet).

## Fonctionnalités

- Écran de connexion local avec validation d’e-mail et mot de passe hashé en SHA-256.
- Session persistée localement et bouton de déconnexion.
- Permission caméra demandée au runtime avec état explicite si elle est refusée.
- Prévisualisation caméra via **CameraX** et capture enregistrée dans l’espace privé de l’application.
- Architecture minimale et extensible : `AuthRepository`, `LoginActivity`, `CameraActivity`.

## Compte de démonstration

- E-mail : `demo@auth.app`
- Mot de passe : `Demo1234!`

> Pour un produit réel, remplacez l’authentification locale par un backend HTTPS (OAuth2/OpenID Connect ou API avec jetons courts et rotation), sans embarquer de secret dans l’APK.

## Construire

```bash
./gradlew assembleDebug
```

L’APK de debug sera généré dans `app/build/outputs/apk/debug/`.
