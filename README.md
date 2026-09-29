# Authentification Android

Application Android native en **Kotlin** avec une interface XML légère (le HTML n’est pas utilisé comme cœur du projet).

L’application ne demande **aucune connexion** et n’utilise aucune base de données distante : un authentificateur 2FA est un coffre local installé sur le téléphone. Les secrets et comptes devront être stockés localement de manière chiffrée dans la version complète ; Internet n’est nécessaire que pour un éventuel module publicitaire.

## Fonctionnalités

- Démarrage direct sur le scanner, sans compte utilisateur ni écran de connexion.
- Permission caméra demandée au runtime avec état explicite si elle est refusée.
- Prévisualisation caméra via **CameraX** et capture enregistrée dans l’espace privé de l’application.
- Architecture minimale et extensible : `AuthRepository`, `LoginActivity`, `CameraActivity`.

> Pour un produit réel, les secrets 2FA doivent être chiffrés dans le stockage local Android (Keystore), sans les envoyer à un backend.

## Construire

```bash
./gradlew assembleDebug
```

L’APK de debug sera généré dans `app/build/outputs/apk/debug/`.
