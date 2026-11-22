# CHK Caméras — signature permanente / mises à jour

La compilation Release utilise le coffre de signature existant du service Render
`modeliseur-trellis-mcp.onrender.com`, sans clé privée dans GitHub.

- Endpoint dédié : `/camera-maison/ci/signing`.
- Authentification : OIDC GitHub vérifiant l'émetteur, l'audience, les identifiants du dépôt/propriétaire, l'acteur, la branche `main` et le workflow `build.yml`.
- Certificat : empreinte SHA-256 publique contrôlée avant chaque compilation.
- Les clés privées, mots de passe et tokens ne doivent jamais apparaître dans les logs ni dans le dépôt.
- APK officielle : `com.chk.cameras`, Release GitHub `v1.0.<numéro de run>`, fichier `chk-cameras-release.apk`.
- Mise à jour à l'ouverture : vérification automatique de la dernière Release toutes les 24h avec proposition de téléchargement et installation Android.
- Les APK Debug `com.chk.cameras.debug` déjà installées ne sont pas remplaçables par des APK Release : **installer une seule fois l'officielle**, qui peut cohabiter avec la Debug. Les données sont ensuite conservées à chaque version signée par le même certificat.
- Une signature permanente n'implique pas que les caméras Aiwit P2P fournissent un flux vidéo : ce protocole reste à rendre compatible et tester avec les caméras réelles.

Le système réutilise pour Camera-maison l'identité de signature existante du coffre Render de CHK Agent Browser/Modéliseur ; il ne génère aucune nouvelle clé et n'altère pas les routes de signature déjà en service.
