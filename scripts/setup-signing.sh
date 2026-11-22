#!/usr/bin/env bash
# Activation unique et sûre des Releases CHK Caméras sur une machine de confiance / Codespaces.
# Ne publie JAMAIS la clé privée ni les mots de passe dans le dépôt GitHub public.
set -euo pipefail
umask 077

REPO="Chasmet/Camera-maison"
SIGN_DIR="$HOME/.chk-cameras-signature"
KEYSTORE="$SIGN_DIR/cameras-release.jks"
PASSWORD_FILE="$SIGN_DIR/password.txt"
KEY_ALIAS="chk_cameras_release"

for tool in gh keytool openssl base64; do
  command -v "$tool" >/dev/null || { echo "Outil requis : $tool" >&2; exit 1; }
done
gh auth status >/dev/null
GH_SECRETS="$(gh secret list --repo "$REPO" --json name --jq '.[].name')"
if grep -qx "CHK_KEYSTORE_BASE64" <<< "$GH_SECRETS"; then
  echo "Une clé de signature GitHub existe déjà. Aucune rotation automatique autorisée."
  echo "Vérifie la présence des quatre secrets puis relance le workflow GitHub."
  exit 0
fi
mkdir -p "$SIGN_DIR"
chmod 700 "$SIGN_DIR"

if [[ -f "$KEYSTORE" && -f "$PASSWORD_FILE" ]]; then
  echo "Réutilisation de la clé locale existante (important pour conserver la signature)."
elif [[ -e "$KEYSTORE" || -e "$PASSWORD_FILE" ]]; then
  echo "Fichiers de signature incomplets : intervention manuelle nécessaire. Aucune nouvelle clé générée." >&2
  exit 1
else
  export CHK_LOCAL_PASSWORD
  CHK_LOCAL_PASSWORD="$(openssl rand -hex 32)"
  printf '%s' "$CHK_LOCAL_PASSWORD" > "$PASSWORD_FILE"
  chmod 600 "$PASSWORD_FILE"
  keytool -genkeypair -noprompt \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=CHK Cameras, O=CHK, C=FR" -storetype PKCS12 \
    -keystore "$KEYSTORE" \
    -storepass:env CHK_LOCAL_PASSWORD -keypass:env CHK_LOCAL_PASSWORD
  chmod 600 "$KEYSTORE"
  unset CHK_LOCAL_PASSWORD
fi

echo "Enregistrement chiffré des quatre secrets GitHub..."
base64 "$KEYSTORE" | tr -d '\n' | gh secret set CHK_KEYSTORE_BASE64 --repo "$REPO"
cat "$PASSWORD_FILE" | gh secret set CHK_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$KEY_ALIAS" | gh secret set CHK_KEY_ALIAS --repo "$REPO"
cat "$PASSWORD_FILE" | gh secret set CHK_KEY_PASSWORD --repo "$REPO"

echo "Déclenchement de la première Release signée..."
gh workflow run build.yml --repo "$REPO" --ref main
echo "Configuration soumise. GitHub Actions va compiler une Release signée."
echo "IMPORTANT : conserve hors ligne ces deux fichiers privés :"
echo "$KEYSTORE"
echo "$PASSWORD_FILE"
echo "Ne les ajoute jamais au dépôt public. Ne change jamais la clé sur les prochaines versions."
