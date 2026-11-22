package com.chk.cameras;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Mise à jour par Releases GitHub, consentement explicite avant installation. */
public final class UpdateManager {
    static final int REQUEST_UNKNOWN_APPS = 9003;
    private final Activity activity;
    private final SessionVault vault;

    UpdateManager(Activity activity, SessionVault vault) {
        this.activity = activity;
        this.vault = vault;
    }

    public void check(boolean manual) {
        // Une version Debug a un identifiant et une signature différents d'une Release.
        // Elle ne peut pas recevoir les APK signés de production par-dessus.
        if (BuildConfig.DEBUG || activity.getPackageName().endsWith(".debug")) {
            if (manual) message("Version de test — mise à jour impossible",
                    "Cette APK est une version Debug. Android ne peut pas la remplacer "
                    + "par la version définitive sans une première installation signée. "
                    + "Tes futures mises à jour fonctionneront ensuite dans CHK Caméras "
                    + "avec la même clé de signature. Ne désinstalle pas cette version "
                    + "avant d'avoir sauvegardé tes paramètres.");
            return;
        }
        // Dépôt fixé à la compilation : aucune configuration manuelle nécessaire.
        String repository = BuildConfig.GITHUB_REPOSITORY.trim();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            if (manual) message("Mises à jour", "Configure d'abord un dépôt GitHub privé ou public dans Options, sous la forme propriétaire/dépôt.");
            return;
        }
        long now = System.currentTimeMillis();
        if (!manual && now - vault.lastUpdateCheck() < 24L * 60 * 60 * 1000) return;
        new Thread(() -> {
            try {
                JSONObject response = getJson("https://api.github.com/repos/" + repository + "/releases/latest");
                String tag = response.optString("tag_name", "");
                vault.setLastUpdateCheck(System.currentTimeMillis());
                if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                    if (manual) message("À jour", "Ta version " + BuildConfig.VERSION_NAME + " est la plus récente sur ce dépôt.");
                    return;
                }
                JSONArray assets = response.optJSONArray("assets");
                String apkUrl = null, digest = "";
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        if ("chk-cameras-release.apk".equals(asset.optString("name"))) {
                            apkUrl = asset.optString("browser_download_url", null);
                            digest = asset.optString("digest", "");
                            break;
                        }
                    }
                }
                if (apkUrl == null) {
                    if (manual) message("Mise à jour incomplète",
                            "La Release GitHub ne contient pas chk-cameras-release.apk. "
                            + "Aucune installation non signée ne sera proposée.");
                    return;
                }
                final String url = apkUrl, hash = digest;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                            .setTitle("Mise à jour " + tag)
                            .setMessage("Nouvelle version disponible. Télécharger depuis GitHub et demander l'installation Android ? Tes données sont conservées si la signature et l'identifiant du paquet sont identiques.")
                            .setNegativeButton("Plus tard", null)
                            .setPositiveButton("Télécharger", (d, which) -> download(url, hash))
                            .show();
                });
            } catch (Exception ex) {
                if ("NO_SIGNED_RELEASE".equals(ex.getMessage())) {
                    vault.setLastUpdateCheck(System.currentTimeMillis());
                    if (manual) message("Pas encore de mise à jour disponible",
                            "Le dépôt " + repository + " ne contient aucune Release signée. "
                            + "La compilation Debug seule ne crée pas une mise à jour installable. "
                            + "Il faut d'abord configurer la clé de signature permanente dans les secrets GitHub.");
                } else if (manual) {
                    message("Vérification impossible",
                            "Connexion à GitHub impossible : " + safeError(ex));
                }
            }
        }, "chk-update-check").start();
    }

    private void download(String source, String expectedDigest) {
        Toast.makeText(activity, "Téléchargement sécurisé en cours…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(source);
                if (!"https".equals(u.getProtocol()) || !"github.com".equals(u.getHost())) {
                    throw new IllegalArgumentException("URL de téléchargement non autorisée");
                }
                File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) throw new IllegalStateException("Stockage indisponible");
                File temporary = new File(directory, "chk-cameras-release.download");
                File destination = new File(directory, UpdateFileProvider.FILE_NAME);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "CHK-Cameras-Updater");
                if (conn.getResponseCode() != 200) throw new IllegalStateException("HTTP " + conn.getResponseCode());
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long count = 0;
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[32768];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        count += len;
                        if (count > 200L * 1024 * 1024) throw new IllegalStateException("APK trop volumineux");
                        sha.update(buffer, 0, len);
                        out.write(buffer, 0, len);
                    }
                    out.getFD().sync();
                }
                if (count < 5000) throw new IllegalStateException("APK incomplet");
                if (expectedDigest.startsWith("sha256:")) {
                    StringBuilder actual = new StringBuilder();
                    for (byte b : sha.digest()) actual.append(String.format(Locale.US, "%02x", b & 255));
                    if (!actual.toString().equalsIgnoreCase(expectedDigest.substring(7))) {
                        temporary.delete();
                        throw new SecurityException("Empreinte SHA-256 incorrecte");
                    }
                }
                if (destination.exists() && !destination.delete()) throw new IllegalStateException("Ancienne version non supprimable");
                if (!temporary.renameTo(destination)) throw new IllegalStateException("Impossible de finaliser le fichier");
                PackageInfo downloaded = activity.getPackageManager().getPackageArchiveInfo(destination.getAbsolutePath(), 0);
                if (downloaded == null || !activity.getPackageName().equals(downloaded.packageName)) {
                    destination.delete();
                    throw new SecurityException("Paquet Android inattendu : installation refusée");
                }
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle("APK téléchargé")
                        .setMessage("Android vérifiera la signature lors de la mise à jour. Continuer ?")
                        .setNegativeButton("Annuler", null)
                        .setPositiveButton("Installer", (d, which) -> install())
                        .show());
            } catch (Exception ex) {
                message("Téléchargement échoué", safeError(ex));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "chk-update-download").start();
    }

    void resumeInstall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls()) install();
    }

    private void install() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Autorisation Android")
                    .setMessage("Autorise temporairement l'installation depuis CHK Caméras. Au retour, Android pourra lancer l'installateur.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Ouvrir les réglages", (d, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivityForResult(intent, REQUEST_UNKNOWN_APPS);
                    }).show();
            return;
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(activity.getPackageName() + ".updates")
                .path("update")
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            message("Installation", "Aucun installateur Android n'a accepté le fichier.");
        }
    }

    private static JSONObject getJson(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(path).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "CHK-Cameras-Android");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = conn.getResponseCode();
            if (status == 404) throw new IllegalStateException("NO_SIGNED_RELEASE");
            if (status != 200) throw new IllegalStateException("GitHub HTTP " + status);
            try (InputStream stream = conn.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int size, total = 0;
                while ((size = stream.read(buffer)) != -1) {
                    if ((total += size) > 400000) throw new IllegalStateException("Réponse GitHub trop volumineuse");
                    bytes.write(buffer, 0, size);
                }
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            conn.disconnect();
        }
    }

    static int compareVersions(String left, String right) {
        String[] a = left.replaceFirst("^v", "").split("[.-]");
        String[] b = right.replaceFirst("^v", "").split("[.-]");
        for (int i = 0; i < 3; i++) {
            int ai = i < a.length && a[i].matches("\\d+") ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length && b[i].matches("\\d+") ? Integer.parseInt(b[i]) : 0;
            if (ai != bi) return ai > bi ? 1 : -1;
        }
        return 0;
    }

    private void message(String title, String body) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) new AlertDialog.Builder(activity)
                    .setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
        });
    }

    private static String safeError(Exception ex) {
        if (ex instanceof SecurityException) return "Fichier non vérifié.";
        String message = ex.getMessage();
        return message == null ? "Erreur réseau ou serveur." : message.substring(0, Math.min(120, message.length()));
    }
}
