package com.chk.cameras;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Client de compatibilité non officiel pour les métadonnées du compte Aiwit.
 * N'implémente PAS le direct vidéo P2P, l'interphone ou le flux chiffré.
 * Aucun serveur personnel ni analytics; TLS standard vérifié par Android.
 */
public final class CloudApi {
    private static final String BASE = "https://api.v2.gdxp.com";
    // Constante de compatibilité observée dans le paquet Aiwit fourni par le propriétaire.
    private static final String LOGIN_SALT = "EKDB_ni&Hb&Zt&zz^7qn9";
    private final String appId;

    public CloudApi(String appId) {
        this.appId = appId;
    }

    public static final class Session {
        public final String sessionId;
        public final String username;
        public final long uid;
        Session(String id, String name, long uid) {
            this.sessionId = id;
            this.username = name;
            this.uid = uid;
        }
    }

    public static final class Device {
        public final String id;
        public final String name;
        public final String firmware;
        public final String model;
        public final String mac;
        public final int battery;
        public final int state;

        Device(JSONObject o) {
            id = o.optString("device_sn", "");
            name = o.optString("name", "Caméra");
            firmware = o.optString("firmware_ver", "");
            model = o.optString("oem", "");
            mac = o.optString("mac", "");
            battery = o.optInt("battery_level", -1);
            state = o.optInt("state", -1);
        }
    }

    public static final class Recording {
        public final String time;
        public final String kind;
        public final String fileName;
        public final long duration;
        public final long size;
        public final String deviceId;

        Recording(JSONObject o) {
            time = o.optString("time", "");
            kind = o.optString("type", "Événement");
            fileName = o.optString("fileName", "");
            duration = o.optLong("duration", 0);
            size = o.optLong("length", 0);
            deviceId = o.optString("deviceSn", "");
        }
    }

    public Session login(String email, String password) throws Exception {
        String hash = md5(email + "/" + password + LOGIN_SALT);
        String form = "username=" + encode(email)
                + "&password=" + encode(password)
                + "&pushToken=pushToken"
                + "&sign=" + encode(hash)
                + "&appSn=" + encode(appId);
        JSONObject envelope = request("POST", "/app_group/login", form);
        JSONObject content = content(envelope);
        String sid = content.optString("session_id", "");
        if (sid.isEmpty()) throw new IOException("La connexion n'a pas retourné de session valide.");
        return new Session(sid, content.optString("username", email), content.optLong("uid"));
    }

    public List<Device> listDevices(String sessionId) throws Exception {
        JSONObject envelope = request("GET", "/app_group/list_v2/"
                + encode(sessionId) + "/" + encode(appId), null);
        JSONObject info = content(envelope);
        JSONArray items = info.optJSONArray("list");
        if (items == null) {
            throw new IOException("Réponse Aiwit : liste d'appareils absente. "
                    + "La connexion au cloud n'est pas suffisante pour synchroniser les caméras.");
        }
        List<Device> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.optJSONObject(i);
                if (obj != null) {
                    Device d = new Device(obj);
                    if (!d.id.isEmpty()) list.add(d);
                }
            }
        }
        if (items.length() > 0 && list.isEmpty()) {
            throw new IOException("Caméras reçues mais identifiants non reconnus par CHK Caméras.");
        }
        return list;
    }

    public List<Recording> listRecordings(String sessionId, String deviceId, String day) throws Exception {
        if (!day.matches("[0-9]{8}")) throw new IOException("Date invalide.");
        Exception lastError = null;
        // Les chemins v9/v10 sont présents dans l'APK ancien, v11 dans des clients récents.
        for (String endpoint : new String[] { "v11", "v10", "v9" }) {
            try {
                String path = "/oss_get_files_" + endpoint + "/" + encode(sessionId)
                        + "/" + day + "/" + encode(deviceId) + "/1/0/0";
                JSONObject info = content(request("GET", path, null));
                JSONArray items = info.optJSONArray("items");
                if (items == null) throw new IOException("Format de liste non reconnu.");
                List<Recording> list = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.optJSONObject(i);
                    if (obj != null) list.add(new Recording(obj));
                }
                return list;
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw lastError == null ? new IOException("Historique indisponible") : lastError;
    }

    private JSONObject request(String method, String path, String form) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setUseCaches(false);
        conn.setRequestProperty("AppName", "aiwit");
        conn.setRequestProperty("AppLang", "en");
        // Version d'en-tête validée par le client Aiwit indépendant (mai 2026).
        conn.setRequestProperty("AppVersion", "3.5.6");
        conn.setRequestProperty("AppID", appId);
        conn.setRequestProperty("OS", "2");
        conn.setRequestProperty("User-Agent", "okhttp/4.10.0");
        if (form != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }
        try {
            if (form != null) {
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(form.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("Serveur Aiwit indisponible (HTTP " + code + ").");
            String data;
            try (InputStream in = conn.getInputStream()) {
                data = readMax(in, 3 * 1024 * 1024);
            }
            return new JSONObject(data);
        } finally {
            conn.disconnect();
        }
    }

    private static JSONObject content(JSONObject json) throws IOException {
        if (json.optInt("resultCode", -1) != 0) {
            String message = json.optString("msg", "Erreur du cloud Aiwit.");
            if (message.contains("账号或密码错误")) {
                message = "Aiwit refuse cette connexion (compte ou mot de passe incorrect selon le serveur). "
                        + "Si les mêmes identifiants fonctionnent dans Aiwit officiel, "
                        + "la connexion non officielle de CHK Caméras reste incompatible.";
            }
            if (message.length() > 180) message = message.substring(0, 180);
            throw new IOException("Service Aiwit : " + message);
        }
        JSONObject info = json.optJSONObject("content");
        if (info == null) throw new IOException("Réponse Aiwit incomplète.");
        return info;
    }

    private static String readMax(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Réponse du serveur trop volumineuse.");
            bytes.write(buffer, 0, read);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String md5(String text) throws Exception {
        byte[] b = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : b) result.append(String.format(java.util.Locale.US, "%02x", item & 255));
        return result.toString();
    }
}
