# CHK Caméras — lecteur vidéo Android personnel (Java)

Dépôt : `Chasmet/Camera-maison`. Application Android Java, Android 5+, compileSdk/targetSdk 34, sans SDK publicitaire.

## Fonctions implémentées

- **Lecteur vidéo dans CHK Caméras**, sans redirection Aiwit : H.264/H.265 selon décodage de l'appareil, RTSP/TCP, HLS ou fichier vidéo HTTP(S) compatible Media3.
- Gestion de profils de caméras locales et de sources de serveur personnel, indépendante du compte Aiwit : ajouter, modifier, supprimer, afficher le direct.
- Possibilité de rattacher une source RTSP/HLS à une caméra Aiwit identifiée dans le cloud, si une passerelle compatible fournit effectivement cette source.
- Gestion native de la lecture, des erreurs et bouton **Reconnecter** ; arrêt propre du flux à la fermeture.
- Chiffrement AES-GCM Android Keystore des URL de flux (y compris identifiants dans les URL) sur Android 6+. Sous Android 5, profils conservés uniquement en mémoire pendant la session.
- Métadonnées Aiwit via endpoints non officiels : connexion facultative, liste, batterie et historique sous réserve de disponibilité.
- Mises à jour GitHub dans l'app : Release signée par CHK Serveur PC Universel v1.0.5 (clé permanente conservée sur le PC, autorisation GitHub OIDC).

## Compatibilité vidéo : état réel

| Source | Situation |
| --- | --- |
| RTSP standard | Lecteur Android implémenté, test matériel non réalisé |
| HLS (.m3u8) | Lecteur Android implémenté, test matériel non réalisé |
| RTSP d'une passerelle personnelle | Lecteur implémenté, passerelle/source à configurer |
| Vidéo P2P native Aiwit/EKEN | **Non implémentée et non validée** : protocole propriétaire `libVCTP2P.so` |
| Interphone Aiwit | Non implémenté |

Les caméras Aiwit ne diffusent pas nécessairement un flux RTSP/ONVIF. Un serveur n'est pas capable de recevoir leur vidéo tant que le pont d'interopérabilité Aiwit n'existe pas. **Aucune simulation de direct n'est fournie.**

## Diagnostic des mises à jour (22 novembre 2026)

**Constat vérifié dans le dépôt** : la liste des Releases GitHub était vide, alors que la compilation Debug n°6 avait réussi. Une compilation Debug n'est pas une Release signée.

- APK de test : `com.chk.cameras.debug`, avec une clé de signature Debug du runner éphémère. Il est impossible de mettre directement la version finale `com.chk.cameras` par-dessus ce paquet ou de garantir la même clé Debug entre deux exécutions.
- Mise à jour définitive : il faut installer une **première** APK Release signée de `com.chk.cameras`. Toutes les mises à jour ultérieures utiliseront cette identité et le même certificat, avec conservation des données.
- Le dépôt des mises à jour est fixé dans l'application : `Chasmet/Camera-maison`. Aucun réglage manuel de l'URL nécessaire.
- L'application indique maintenant explicitement quand elle est en mode Debug et quand aucune Release signée n'est disponible. Le bouton « Mise à jour » est accessible dès l'accueil.
- La signature stable requiert une création unique d'une clé de signature **hors du dépôt public**, puis les quatre secrets GitHub ci-dessous. **Ne jamais publier de clé privée ou mot de passe dans ce dépôt public.**
- Les contrôles de signature d'Android et la confirmation système sont obligatoires ; l'application ne peut pas installer silencieusement son APK.

## Signatures et mises à jour

Les secrets GitHub `CHK_KEYSTORE_BASE64`, `CHK_KEYSTORE_PASSWORD`, `CHK_KEY_ALIAS` et `CHK_KEY_PASSWORD` doivent contenir **la même** clé de signature à chaque version. Le workflow `.github/workflows/build.yml` compile Debug, puis Release signée si les secrets sont disponibles, et publie la Release. L'installation par-dessus l'APK déjà présent exige le même `applicationId` et la même signature.

## Projet

```text
app/src/main/java/com/chk/cameras/
  MainActivity.java       caméras, compte et gestion des flux
  LiveActivity.java       lecture intégrée RTSP / HLS, reconnexion
  StreamStore.java        sources vidéo et chiffrement local
  CloudApi.java           métadonnées du cloud Aiwit (non officiel)
  SessionVault.java       session Aiwit et favoris
  UpdateManager.java      mises à jour GitHub installables
  UpdateFileProvider.java installateur Android
app/src/main/res/layout/
  activity_main.xml       accueil, caméras et ajout de flux
  activity_live.xml       lecteur vidéo
  row_device.xml          cellule de caméra
  row_recording.xml       événement Aiwit
  dialog_login.xml        connexion facultative
bridge/
  README.md               guide du pont vidéo privé
  go2rtc.yaml.example     exemple serveur RTSP sans secrets
.github/workflows/build.yml
app/build.gradle
build.gradle
settings.gradle
gradle/wrapper/gradle-wrapper.properties
```

Compiler avec `./gradlew :app:assembleDebug` en disposant du SDK Android 34 et de Gradle 8.7. Le workflow GitHub Actions le fait automatiquement sur le runner.

## Sécurité

Ne jamais publier les adresses privées, mots de passe RTSP ou jetons Aiwit dans GitHub. Les flux HTTP locaux non chiffrés ne doivent circuler que dans un LAN de confiance ou un VPN privé. Préférer HTTPS pour une passerelle HTTP accessible hors du domicile. Les profils restent sur le téléphone et ne sont pas publiés dans les Releases.

## Signature de production (CHK Serveur Universel)
Le backend du dépôt `Chasmet/CHK-serveur-Universal-Control` expose `/camera/api/ci/signing` sur le PC Tailscale. Voir [SIGNATURE-OIDC.md](SIGNATURE-OIDC.md). L'ancienne procédure Render et la création de secrets GitHub ont été désactivées.
