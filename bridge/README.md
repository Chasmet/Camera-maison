# Passerelle vidéo facultative CHK Caméras

**Le pont vidéo permet à une caméra qui fournit déjà une source RTSP de diffuser dans l'application Android, sans lancer d'application externe.**

## Fonctionnement

Caméra RTSP -> go2rtc sur le PC / serveur personnel -> RTSP privé -> lecteur natif Media3 dans CHK Caméras.

1. Installer `go2rtc` depuis le dépôt officiel `https://github.com/AlexxIT/go2rtc` sur le serveur personnel.
2. Copier `go2rtc.yaml.example` vers `go2rtc.yaml` hors du dépôt public, puis remplacer les paramètres de la caméra.
3. Autoriser le port 8554 **uniquement sur le réseau local ou un VPN privé** (par exemple Tailscale). Ne pas exposer ce port sur Internet.
4. Dans CHK Caméras, ajouter `rtsp://IP_DU_SERVEUR_PRIVE:8554/maison` et lancer le direct dans l'application.

## Limite impérative

**Ce pont ne rend pas Aiwit/EKEN compatible à lui seul.** L'APK Aiwit fourni utilise `libVCTP2P.so` et un chemin vidéo chiffré. Sans décodeur ou passerelle Aiwit réellement opérationnel, ni le smartphone ni go2rtc ne connaissent la vidéo à récupérer. Ne pas annoncer la lecture P2P comme fonctionnelle avant un test avec les appareils concernés.

Le serveur existant de l'utilisateur n'est **pas modifié** par ce module. Le module est fourni isolément, sans routes réseau ni secrets.
