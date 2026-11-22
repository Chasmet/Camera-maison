# CHK Caméras — Releases Android sur CHK Serveur PC Universel

Le serveur de signature est **le PC personnel CHK Universel**, accessible par son adresse Tailscale :
`https://chk-universal-1.taild9e4de.ts.net/camera/api/ci/signing`.

Le module est dans le dépôt `Chasmet/CHK-serveur-Universal-Control`, fichier `src/camera.mjs`, isolé des routes de CHK Browser et XDebt. Il crée une unique clé PKCS12 sur Windows (via PowerShell) lors de la première demande autorisée par un jeton OIDC signé de GitHub Actions. La clé reste durablement dans les fichiers privés du serveur et doit être sauvegardée séparément : ne jamais publier le PFX, le mot de passe ou le JWT.

Le workflow `Camera-maison/.github/workflows/build.yml` utilise le certificat persistant à chaque Release, vérifie `apksigner` et compare son empreinte à l'APK précédente. Une rotation de certificat bloque le build plutôt que de casser les mises à jour.

Le programme **CHK SERVEUR PC UNIVERSEL v1.0.5** doit être installé une fois sur le PC ; la simple mise à jour du dépôt GitHub ne remplace pas le serveur déjà exécuté. Il faut ensuite que GitHub Actions puisse atteindre cette URL HTTPS : un accès Tailscale privé seul n'est pas suffisant pour des runners GitHub publics.

L'application CHK Caméras installée actuellement est `com.chk.cameras.debug` et ne peut pas être remplacée par la Release officielle `com.chk.cameras`. La première APK officielle s'installe séparément ; les versions officielles suivantes se mettent à jour **dans l'application** et conservent les données si le certificat et l'identifiant restent identiques.

**Limite actuelle distincte :** la vidéo propriétaire Aiwit P2P n'est pas encore décodée. Aucun direct réel n'est garanti par la seule présence du serveur.
