#!/usr/bin/env python3
"""Fetch the permanent Android signer from the existing isolated Render vault.

Strict GitHub Actions OIDC: camera-maison only; never commit or print private keys.
The same key/certificate is reused on every signed Camera-maison release.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

AUDIENCE = "https://modeliseur-trellis-mcp.onrender.com/camera-maison/ci/signing"
EXPECTED_SHA256 = "29e4bf6469e65741750b25f7ea50c97e7b538d5ead63bffebfd93cf197dd9335"
REQUIRED = ("keystoreBase64", "storePassword", "keyPassword", "alias")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def fetch_json(url, bearer, timeout):
    request = urllib.request.Request(
        url, headers={"Authorization": "Bearer " + bearer,
                      "User-Agent": "CHK-Camera-maison-CI/1.0",
                      "Accept": "application/json"})
    with OPENER.open(request, timeout=timeout) as response:
        if response.status != 200:
            raise RuntimeError("Serveur de signature: réponse non autorisée")
        if int(response.headers.get("Content-Length", "0") or "0") > 500_000:
            raise RuntimeError("Réponse trop volumineuse")
        raw = response.read(500_001)
        if len(raw) > 500_000:
            raise RuntimeError("Réponse trop volumineuse")
    return json.loads(raw)


def get_signing():
    token_url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
    token_url += ("&" if "?" in token_url else "?") + urllib.parse.urlencode(
        {"audience": AUDIENCE})
    for attempt in range(7):
        try:
            identity = fetch_json(
                token_url, os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"], 20)["value"]
            return fetch_json(AUDIENCE, identity, 45)
        except urllib.error.HTTPError as error:
            if error.code not in {404, 429, 500, 502, 503, 504} or attempt == 6:
                raise RuntimeError("Signature permanente : HTTP " + str(error.code)) from None
            print("Attente du déploiement Render (HTTP " + str(error.code) + ")")
        except (TimeoutError, urllib.error.URLError, ConnectionError):
            if attempt == 6:
                raise RuntimeError("Serveur de signature indisponible") from None
            print("Connexion Render différée ; nouvel essai")
        time.sleep(min(8 + 4 * attempt, 24))
    raise RuntimeError("Signature permanente non disponible")


def main():
    data = get_signing()
    if not isinstance(data, dict) or any(
            not isinstance(data.get(k), str) or not data[k] for k in REQUIRED):
        raise RuntimeError("Matériel de signature incomplet")
    raw = base64.b64decode(data["keystoreBase64"], validate=True)
    if not 100 < len(raw) < 500_000:
        raise RuntimeError("Format de clé privée incorrect")
    alias = data["alias"]
    store_pass = data["storePassword"]
    key_pass = data["keyPassword"]
    print("::add-mask::" + store_pass)
    print("::add-mask::" + key_pass)
    folder = Path(os.environ["RUNNER_TEMP"])
    location = folder / "camera-maison-release.jks"
    fd = os.open(str(location), os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(raw)
    environment = {**os.environ, "CHK_KEYSTORE_PASSWORD": store_pass}
    certificate = subprocess.check_output(
        ["keytool", "-exportcert", "-keystore", str(location),
         "-alias", alias, "-storepass:env", "CHK_KEYSTORE_PASSWORD"],
        env=environment, stderr=subprocess.DEVNULL)
    actual = hashlib.sha256(certificate).hexdigest()
    if actual != EXPECTED_SHA256:
        location.unlink(missing_ok=True)
        raise RuntimeError("Certificat Android non autorisé : Release refusée")
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as destination:
        for key, value in (
            ("CHK_KEYSTORE_PATH", str(location)),
            ("CHK_KEYSTORE_PASSWORD", store_pass),
            ("CHK_KEY_ALIAS", alias),
            ("CHK_KEY_PASSWORD", key_pass),
        ):
            destination.write(key + "=" + value + "\n")
    print("Signature permanente approuvée pour Camera-maison : " + actual[:12])


if __name__ == "__main__":
    main()
