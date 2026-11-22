# Activer une fois les mises à jour signées

## Pourquoi l'ancien APK n'est pas actualisable

L'APK de test créé par `assembleDebug` porte `com.chk.cameras.debug` et la signature temporaire du runner GitHub Actions. Une APK officielle `com.chk.cameras` ne peut pas remplacer cette installation. Il faut réaliser une première installation de la version Release signée (en prenant garde aux données enregistrées dans le Debug).

## Initialiser la signature permanente

Sur un terminal de confiance ou GitHub Codespaces connecté au dépôt `Chasmet/Camera-maison`, lancer :

```bash
bash scripts/setup-signing.sh
```

Le script génère **une seule** clé locale, configure les quatre secrets chiffrés GitHub via GitHub CLI et déclenche `.github/workflows/build.yml`. Il refuse de remplacer une clé GitHub existante. Conserver une sauvegarde privée et durable du keystore et de son mot de passe. Ne jamais les publier dans un dépôt public ni les partager dans une conversation.

Une fois la Release créée dans `https://github.com/Chasmet/Camera-maison/releases`, installer une seule fois cette version officielle. Toutes les versions suivantes gardant le même identifiant d'application et le même certificat seront proposées dans CHK Caméras, avec confirmation par le système Android.

La vérification automatique ne peut pas forcer une installation silencieuse ni contourner les autorisations Android. Elle ne dépend pas d'un serveur personnel Render.
