# CHK Caméras — application Android personnelle (Java)

Application indépendante pensée pour les propriétaires de caméras liées à **Aiwit / EKEN**.
Elle n'utilise pas le code ni les bibliothèques propriétaires de l'application analysée.

## Fonctions livrées dans le code source

- Connexion au **compte Aiwit existant** via HTTPS (interface cloud non officielle).
- Liste des caméras, modèle, version de firmware et niveau de batterie, selon les données du serveur.
- Favoris, recherche, noms personnalisés enregistrés **uniquement sur le téléphone**.
- Consultation des **métadonnées** de l'historique par caméra et par jour (selon droits cloud).
- Export CSV local des événements via le sélecteur de fichiers Android.
- Synchronisation espacée de 2 min **seulement pendant que l'application est ouverte**.
- Pas de publicité, pas de SDK analytics, pas d'accès au micro ou aux photos du téléphone.
- Conservation du jeton de session (pas du mot de passe) avec Android Keystore sur API 23+.
- Lien explicite vers l'application Aiwit officielle pour le **direct vidéo et l'audio**.
- Vérification quotidienne des Releases GitHub configurées et proposition de téléchargement/installation.

## Limites importantes

**La vidéo en direct, l'interphone, la commande PTZ, les alertes push temps réel et les vidéos chiffrées `.ts2` ne sont PAS implémentés.** Ces fonctions utilisent des mécanismes propriétaires et/ou chiffrés. L'APK analysé ne fournit pas une API officielle autorisant leur réutilisation.

Les endpoints REST listés dans `CloudApi.java` sont **non documentés par le fabricant**. La connexion, l'état des caméras et l'historique peuvent cesser de fonctionner avec une évolution du cloud. Le projet n'a pas été testé avec les identifiants du propriétaire ni avec ses caméras.

En compilation GitHub Actions, le dépôt GitHub de mises à jour est **intégré automatiquement** à l’APK. Pour une compilation manuelle, il reste configurable dans Options. Ce projet ne crée pas automatiquement le dépôt. Aucune Release ni APK compilé n'est fourni avec les sources.

## Structure

```text
Camera-maison/
├── .github/workflows/build.yml
├── README.md
├── AUDIT_APK.md
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/chk/cameras/
│       │   ├── MainActivity.java
│       │   ├── CloudApi.java
│       │   ├── SessionVault.java
│       │   ├── UpdateManager.java
│       │   └── UpdateFileProvider.java
│       └── res/
│           ├── layout/activity_main.xml
│           ├── layout/dialog_login.xml
│           ├── layout/row_device.xml
│           ├── layout/row_recording.xml
│           ├── drawable/card_background.xml
│           ├── drawable/ic_launcher.xml
│           └── values/colors.xml, strings.xml, styles.xml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Compiler

Pré-requis : JDK 17, Android SDK API 34, Gradle 8.7 avec accès réseau pour le téléchargement des plugins Android.

```bash
# Si le wrapper officiel n'est pas encore généré :
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

Le dépôt contient un lanceur `gradlew` de secours utilisant une installation Gradle existante. Le workflow GitHub Actions génère un wrapper officiel avant sa compilation.

**Important :** le fichier Debug porte le package `com.chk.cameras.debug` et n'est pas destiné à recevoir les mises à jour Release. Pour conserver les données, installer dès le départ l'APK Release signé.

## Signer toutes les versions avec la même clé (une seule fois)

Création locale d'une clé (à conserver en lieu sûr, jamais dans GitHub) :

```bash
keytool -genkeypair -keystore chk-release.jks -alias chk-cameras \
  -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12
```

Dans le dépôt GitHub, configurer **Actions secrets** :

- `CHK_KEYSTORE_BASE64` : base64 du fichier JKS (sur Linux : `base64 -w 0 chk-release.jks`).
- `CHK_KEYSTORE_PASSWORD` : mot de passe du magasin.
- `CHK_KEY_ALIAS` : `chk-cameras` (ou votre alias).
- `CHK_KEY_PASSWORD` : mot de passe de la clé.

Avec une clé stable configurée dans GitHub Secrets, chaque commit sur main construit et publie automatiquement une nouvelle Release signée :

```bash
# GitHub Actions automatise la version et la publication
```

Le workflow crée `chk-cameras-release.apk`, l'envoie dans une **GitHub Release** et l'associe à son empreinte SHA-256. Pour les versions ultérieures, **sans changer la clé ni l'applicationId**.

Dans l'application : `Options > Configurer le dépôt GitHub`, renseigner `propriétaire/dépôt`. Les Releases privées ne sont pas récupérables sans authentification GitHub : le mécanisme présent exige une Release publiquement accessible ; ne stockez aucune donnée de caméra dans ce dépôt public.

## Confidentialité et sécurité

- Les identifiants de connexion transitent en HTTPS **vers le cloud Aiwit**. Ne pas utiliser un mot de passe réutilisé ailleurs.
- Les noms et favoris sont stockés en préférences locales, les jetons de session sont chiffrés sur Android 6+.
- Les métadonnées de vidéo sont consultées seulement pour les caméras de votre compte.
- Android vérifie la signature d'une mise à jour par-dessus une application existante ; le code vérifie également la somme SHA-256 si GitHub la publie.
- Aucun serveur Render, abonnement ou clé API personnelle à configurer.

**Note de compatibilité :** l'application Aiwit du fabricant reste nécessaire pour l'appairage Wi-Fi/Bluetooth et le direct vidéo de ses caméras propriétaires.

## Dépôt
Source : https://github.com/Chasmet/Camera-maison. Ne jamais publier de mot de passe, jeton de caméra ni clé de signature.
