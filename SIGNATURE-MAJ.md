# Mises à jour CHK Caméras : configuration actuelle

Le système de signature permanent utilise CHK Serveur PC Universel sur Windows, **sans Render**.

- Installer une fois le serveur universel v1.0.5, conserver son répertoire de données privé.
- La première Release officielle sera générée par GitHub Actions dès que le serveur répondra à la requête OIDC authentifiée depuis le runner.
- Installer une fois la véritable APK Release `com.chk.cameras` depuis GitHub Releases. Elle peut coexister avec `com.chk.cameras.debug` mais ne peut pas être une mise à jour directe de cette APK Debug.
- Ensuite, CHK Caméras vérifie `releases/latest` depuis l'application et propose l'APK officielle signée, sans redirection vers Aiwit et sans désinstallation.
- Ne pas réutiliser l'ancien script `scripts/setup-signing.sh` ; il est désactivé.

La vidéo Aiwit P2P reste un chantier séparé.
