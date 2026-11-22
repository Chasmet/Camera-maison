# CHK Caméras — lecteur vidéo Android personnel (Java)

Dépôt : `Chasmet/Camera-maison`. Application Android Java, Android 5+, compileSdk/targetSdk 34, sans SDK publicitaire.

## Fonctions implémentées

- **Lecteur vidéo dans CHK Caméras**, sans redirection Aiwit : H.264/H.265 selon décodage de l'appareil, RTSP/TCP, HLS ou fichier vidéo HTTP(S) compatible Media3.
- Gestion de profils de caméras locales et de sources de serveur personnel, indépendante du compte Aiwit : ajouter, modifier, supprimer, afficher le direct.
- Possibilité de rattacher une source RTSP/HLS à une caméra Aiwit identifiée dans le cloud, si une passerelle compatible fournit effectivement cette source.
- Gestion native de la lecture, des erreurs et bouton **Reconnecter** ; arrêt propre du flux à la fermeture.
- Chiffrement AES-GCM Android Keystore des URL de flux (y compris identifiants dans les URL) sur Android 6+. Sous Android 5, profils conservés uniquement en mémoire pendant la session.
- Métadonnées Aiwit via endpoints non officiels : connexion facultative, liste, batterie et historique sous réserve de disponibilité.
- Vérification des mises à jour GitHub depuis l'app ; construction Debug via Actions, Release signée **uniquement** lorsque les secrets sont installés.

## Compatibilité vidéo : état réel

| Source | Situation |
| --- | --- |
| RTSP standard | Lecteur Android implémenté, test matériel non réalisé |
| HLS (.m3u8) | Lecteur Android implémenté, test matériel non réalisé |
| RTSP d'une passerelle personnelle | Lecteur implémenté, passerelle/source à configurer |
| Vidéo P2P native Aiwit/EKEN | **Non implémentée et non validée** : protocole propriétaire `libVCTP2P.so` |
| Interphone Aiwit | Non implémenté |

Les caméras Aiwit ne diffusent pas nécessairement un flux RTSP/ONVIF. Un serveur n'est pas capable de recevoir leur vidéo tant que le pont d'interopérabilité Aiwit n'existe pas. **Aucune simulation de direct n'est fournie.**

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
