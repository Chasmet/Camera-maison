package com.chk.cameras;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Conserve seulement le jeton de session, pas le mot de passe Aiwit. */
public final class SessionVault {
    private static final String KEY_ALIAS = "chk_cameras_session_v1";
    private final SharedPreferences prefs;

    public SessionVault(Context ctx) {
        prefs = ctx.getSharedPreferences("chk_cameras_private", Context.MODE_PRIVATE);
    }

    public String appId() {
        String value = prefs.getString("app_id", null);
        if (value == null) {
            value = "APK_" + java.util.UUID.randomUUID().toString();
            prefs.edit().putString("app_id", value).apply();
        }
        return value;
    }

    public void save(CloudApi.Session session) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(session.sessionId.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString("sid_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("sid_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString("username", session.username)
                .apply();
    }

    public String load() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        String data = prefs.getString("sid_data", null);
        String iv = prefs.getString("sid_iv", null);
        if (data == null || iv == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            clear();
            return null;
        }
    }

    public String username() {
        return prefs.getString("username", "");
    }

    public void clear() {
        prefs.edit().remove("sid_data").remove("sid_iv").remove("username").apply();
    }

    public boolean isFavorite(String id) {
        return prefs.getBoolean("favorite_" + id, false);
    }

    public void setFavorite(String id, boolean state) {
        prefs.edit().putBoolean("favorite_" + id, state).apply();
    }

    public String label(String id) {
        return prefs.getString("label_" + id, "");
    }

    public void setLabel(String id, String label) {
        prefs.edit().putString("label_" + id, label).apply();
    }

    public String repository() {
        return prefs.getString("repository", BuildConfig.GITHUB_REPOSITORY);
    }

    public void setRepository(String repo) {
        prefs.edit().putString("repository", repo).apply();
    }

    public long lastUpdateCheck() {
        return prefs.getLong("update_check", 0L);
    }

    public void setLastUpdateCheck(long date) {
        prefs.edit().putLong("update_check", date).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.Key stored = ks.getKey(KEY_ALIAS, null);
        if (stored != null) return (SecretKey) stored;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
