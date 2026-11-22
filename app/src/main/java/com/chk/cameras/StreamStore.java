package com.chk.cameras;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Flux vidéo choisis par le propriétaire; aucune donnée envoyée à GitHub. */
public final class StreamStore {
    private static final String KEY_ALIAS = "chk_cam_stream_urls_v1";
    private final SharedPreferences prefs;
    private static final java.util.Map<String, String> temporary = new java.util.concurrent.ConcurrentHashMap<>();

    public static final class Profile {
        public final String id;
        public final String name;
        public final String uri;
        public final boolean manual;
        Profile(String id, String name, String uri, boolean manual) {
            this.id = id;
            this.name = name;
            this.uri = uri;
            this.manual = manual;
        }
    }

    public StreamStore(Context context) {
        prefs = context.getSharedPreferences("chk_cameras_streams", Context.MODE_PRIVATE);
    }

    public static boolean isSupported(String address) {
        try {
            android.net.Uri uri = android.net.Uri.parse(address.trim());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("rtsp")
                    || scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null && !uri.getHost().trim().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String createManual(String name, String address) throws Exception {
        String id = "direct:" + UUID.randomUUID().toString();
        save(id, name, address, true);
        return id;
    }

    public void save(String id, String name, String address, boolean manual) throws Exception {
        if (!isSupported(address)) throw new IllegalArgumentException("Adresse RTSP ou HTTP(S) incorrecte.");
        JSONObject json = new JSONObject();
        json.put("name", name.trim().isEmpty() ? "Caméra" : name.trim());
        json.put("uri", address.trim());
        json.put("manual", manual);
        String payload = json.toString();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String combined = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString("profile:" + id, combined).apply();
        } else {
            // Android 5 ne possède pas une clé AES/GCM Keystore stable : mémoire seulement.
            temporary.put(id, payload);
        }
        JSONArray index = readIndex();
        boolean exists = false;
        for (int i = 0; i < index.length(); i++) {
            if (id.equals(index.optString(i))) exists = true;
        }
        if (!exists) {
            index.put(id);
            prefs.edit().putString("index", index.toString()).apply();
        }
    }

    public Profile get(String id) {
        try {
            String value;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                value = temporary.get(id);
            } else {
                String raw = prefs.getString("profile:" + id, null);
                if (raw == null) return null;
                String[] pair = raw.split("\\.", 2);
                if (pair.length != 2) return null;
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getKey(),
                        new GCMParameterSpec(128, Base64.decode(pair[0], Base64.NO_WRAP)));
                value = new String(cipher.doFinal(Base64.decode(pair[1], Base64.NO_WRAP)),
                        StandardCharsets.UTF_8);
            }
            if (value == null) return null;
            JSONObject obj = new JSONObject(value);
            return new Profile(id, obj.optString("name", "Caméra"),
                    obj.optString("uri", ""), obj.optBoolean("manual", false));
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<Profile> manualProfiles() {
        List<Profile> out = new ArrayList<>();
        JSONArray index = readIndex();
        for (int i = 0; i < index.length(); i++) {
            Profile profile = get(index.optString(i));
            if (profile != null && profile.manual) out.add(profile);
        }
        return out;
    }

    public void delete(String id) {
        temporary.remove(id);
        JSONArray index = readIndex();
        JSONArray next = new JSONArray();
        for (int i = 0; i < index.length(); i++) {
            if (!id.equals(index.optString(i))) next.put(index.optString(i));
        }
        prefs.edit().remove("profile:" + id).putString("index", next.toString()).apply();
    }

    private JSONArray readIndex() {
        try { return new JSONArray(prefs.getString("index", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private SecretKey getKey() throws Exception {
        KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        java.security.Key existing = keystore.getKey(KEY_ALIAS, null);
        if (existing != null) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
