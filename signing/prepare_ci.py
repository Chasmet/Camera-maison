#!/usr/bin/env python3
"""Get CHK Caméras' permanent PKCS12 from the user's CHK Universal PC.

The service only issues it after verifying the exact GitHub Actions OIDC JWT.
Private material never enters the public source repository.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request

AUDIENCE = "https://chk-universal-1.taild9e4de.ts.net/camera/api/ci/signing"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def get_json(url, bearer, timeout=30):
    if not (url.startswith("https://") and
            (url.startswith("https://chk-universal-1.taild9e4de.ts.net/") or
             url.startswith(os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"].split("?")[0]))):
        raise RuntimeError("Source de signature inattendue")
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + bearer,
        "Accept": "application/json",
        "User-Agent": "CHK-Cameras-CI/2.0"
    })
    try:
        with OPENER.open(req, timeout=timeout) as response:
            if response.status != 200:
                raise RuntimeError("Service de signature refuse la demande")
            raw = response.read(500001)
        if len(raw) > 500000:
            raise RuntimeError("Réponse de signature trop volumineuse")
        return json.loads(raw)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise RuntimeError("Module /camera/api absent du serveur CHK Universel du PC : mettre à jour le programme Windows v1.0.5") from None
        if error.code in (401, 403):
            raise RuntimeError("Identité OIDC du workflow GitHub refusée par le serveur CHK Universel") from None
        if error.code == 503:
            raise RuntimeError("Signature permanente indisponible sur le PC CHK Universel") from None
        raise RuntimeError("Serveur CHK Universel : HTTP " + str(error.code)) from None
    except urllib.error.URLError:
        raise RuntimeError("Serveur CHK Universel inaccessible depuis GitHub Actions : vérifier HTTPS Tailscale et l'accès du runner") from None


def main():
    request_url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
    token_url = request_url + ("&" if "?" in request_url else "?") + urllib.parse.urlencode({"audience": AUDIENCE})
    identity = get_json(token_url, os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"], 20)["value"]
    response = get_json(AUDIENCE, identity, 45)
    fields = ("keystoreBase64", "storePassword", "keyPassword")
    if not isinstance(response, dict) or any(not isinstance(response.get(f), str) or not response[f] for f in fields):
        raise RuntimeError("La signature PKCS12 de CHK Universel est incomplète")
    if response.get("storeType") != "PKCS12":
        raise RuntimeError("Keystore attendu : PKCS12")
    key = base64.b64decode(response["keystoreBase64"], validate=True)
    if not (400 < len(key) < 100000):
        raise RuntimeError("Fichier de signature invalide")
    path = Path(os.environ["RUNNER_TEMP"]) / "chk-cameras-official-release.p12"
    with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as stream:
        stream.write(key)
    password = response["storePassword"]
    key_password = response["keyPassword"]
    print("::add-mask::" + password)
    print("::add-mask::" + key_password)
    environment = {**os.environ, "CHK_CAMERA_STORE_PASS": password}
    cmd = ["keytool", "-list", "-v", "-storetype", "PKCS12", "-keystore", str(path),
           "-storepass:env", "CHK_CAMERA_STORE_PASS"]
    details = subprocess.check_output(cmd, env=environment, stderr=subprocess.DEVNULL, text=True)
    aliases = re.findall(r"(?mi)^Alias name:\s*(\S+)\s*$", details)
    if len(aliases) != 1 or "Entry type: PrivateKeyEntry" not in details:
        raise RuntimeError("PKCS12 : une seule clé privée attendue")
    alias = aliases[0].strip()
    certificate = subprocess.check_output([
        "keytool", "-exportcert", "-storetype", "PKCS12", "-keystore", str(path),
        "-alias", alias, "-storepass:env", "CHK_CAMERA_STORE_PASS"
    ], env=environment, stderr=subprocess.DEVNULL)
    fingerprint = hashlib.sha256(certificate).hexdigest()
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf8") as output:
        for name, value in (
            ("CHK_KEYSTORE_PATH", str(path)),
            ("CHK_KEYSTORE_PASSWORD", password),
            ("CHK_KEY_ALIAS", alias),
            ("CHK_KEY_PASSWORD", key_password),
        ):
            output.write(name + "=" + value + "\n")
    print("Certificat CHK Caméras persistant vérifié (SHA256): " + fingerprint)


if __name__ == "__main__":
    main()
