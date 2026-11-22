package com.chk.cameras;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Console personnelle pour son propre compte Aiwit.
 * Métadonnées fonctionnelles sous réserve de compatibilité de l'API non officielle.
 * Vidéo live / audio P2P restent dans l'application Aiwit du fabricant.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_CSV = 1001;
    private static final String OFFICIAL_APP = "com.eken.aiwit";
    private final ExecutorService tasks = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<CloudApi.Device> devices = new ArrayList<>();
    private final List<CloudApi.Recording> recordings = new ArrayList<>();
    private final Calendar selectedDay = Calendar.getInstance();
    private final Runnable refreshTicker = new Runnable() {
        @Override public void run() {
            if (sessionId != null && !busy) refreshDevices();
            handler.postDelayed(this, 120000);
        }
    };

    private SessionVault vault;
    private CloudApi cloud;
    private UpdateManager updater;
    private String sessionId;
    private String user;
    private CloudApi.Device selectedDevice;
    private boolean busy;
    private TextView status, deviceTitle, historyTitle, historyInfo;
    private EditText search;
    private CheckBox favorites;
    private LinearLayout deviceContainer, historyPanel, recordingContainer;
    private Button loginButton, refreshButton, dateButton, exportButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vault = new SessionVault(this);
        cloud = new CloudApi(vault.appId());
        updater = new UpdateManager(this, vault);
        sessionId = vault.load();
        user = vault.username();

        status = findViewById(R.id.tvStatus);
        deviceTitle = findViewById(R.id.tvDevicesTitle);
        historyTitle = findViewById(R.id.tvHistoryTitle);
        historyInfo = findViewById(R.id.tvHistoryInfo);
        search = findViewById(R.id.inputSearch);
        favorites = findViewById(R.id.checkFavorites);
        deviceContainer = findViewById(R.id.deviceContainer);
        historyPanel = findViewById(R.id.historyPanel);
        recordingContainer = findViewById(R.id.recordingContainer);
        loginButton = findViewById(R.id.btnLogin);
        refreshButton = findViewById(R.id.btnRefresh);
        dateButton = findViewById(R.id.btnDate);
        exportButton = findViewById(R.id.btnExport);

        loginButton.setOnClickListener(v -> showLogin());
        refreshButton.setOnClickListener(v -> refreshDevices());
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());
        favorites.setOnCheckedChangeListener((b, checked) -> renderDevices());
        dateButton.setOnClickListener(v -> chooseDate());
        exportButton.setOnClickListener(v -> exportCsv());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderDevices();
            }
            @Override public void afterTextChanged(Editable e) { }
        });

        updateSessionUI();
        if (sessionId != null) refreshDevices();
        else showWelcome();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTicker);
        handler.postDelayed(refreshTicker, 120000);
        if (updater != null) updater.check(false);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTicker);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refreshTicker);
        tasks.shutdownNow();
        super.onDestroy();
    }

    private void showWelcome() {
        deviceContainer.removeAllViews();
        showMessageIn(deviceContainer, "Connecte ton compte Aiwit pour récupérer la liste de tes caméras.");
    }

    private void updateSessionUI() {
        boolean connected = sessionId != null;
        status.setText(connected ? "Session : " + user : "Compte Aiwit non connecté");
        loginButton.setText(connected ? "Changer de compte" : "Connexion Aiwit");
        refreshButton.setEnabled(connected && !busy);
    }

    private void showLogin() {
        View root = LayoutInflater.from(this).inflate(R.layout.dialog_login, null);
        EditText email = root.findViewById(R.id.editEmail);
        EditText password = root.findViewById(R.id.editPassword);
        if (user != null) email.setText(user);
        new AlertDialog.Builder(this)
                .setTitle("Compte Aiwit")
                .setView(root)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Se connecter", (dialog, which) -> {
                    String address = email.getText().toString().trim();
                    String secret = password.getText().toString();
                    password.setText("");
                    if (address.isEmpty() || secret.isEmpty()) {
                        info("Connexion", "Saisis ton identifiant et ton mot de passe Aiwit.");
                        return;
                    }
                    setBusy(true);
                    tasks.execute(() -> {
                        try {
                            CloudApi.Session session = cloud.login(address, secret);
                            try { vault.save(session); } catch (Exception ignored) { }
                            runOnUiThread(() -> {
                                sessionId = session.sessionId;
                                user = session.username;
                                selectedDevice = null;
                                historyPanel.setVisibility(View.GONE);
                                setBusy(false);
                                updateSessionUI();
                                refreshDevices();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                setBusy(false);
                                info("Connexion impossible", friendlyError(e));
                            });
                        }
                    });
                }).show();
    }

    private void refreshDevices() {
        if (busy) return;
        if (sessionId == null) { showLogin(); return; }
        setBusy(true);
        final String currentSession = sessionId;
        status.setText("Actualisation des appareils…");
        tasks.execute(() -> {
            try {
                List<CloudApi.Device> fresh = cloud.listDevices(currentSession);
                runOnUiThread(() -> {
                    if (!currentSession.equals(sessionId)) return;
                    devices.clear();
                    devices.addAll(fresh);
                    setBusy(false);
                    status.setText("Compte : " + user + " · mise à jour "
                            + new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date()));
                    renderDevices();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("Impossible d'actualiser les caméras");
                    info("Synchronisation", friendlyError(e)
                            + "\n\nEn cas de session expirée, reconnecte ton compte.");
                });
            }
        });
    }

    private void setBusy(boolean state) {
        busy = state;
        refreshButton.setEnabled(sessionId != null && !state);
        loginButton.setEnabled(!state);
    }

    private String name(CloudApi.Device device) {
        String label = vault.label(device.id);
        return label.isEmpty() ? device.name : label;
    }

    private void renderDevices() {
        deviceContainer.removeAllViews();
        String filter = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (CloudApi.Device d : devices) {
            if (favorites.isChecked() && !vault.isFavorite(d.id)) continue;
            if (!filter.isEmpty() && !name(d).toLowerCase(Locale.ROOT).contains(filter)
                    && !d.name.toLowerCase(Locale.ROOT).contains(filter)) continue;
            View row = getLayoutInflater().inflate(R.layout.row_device, deviceContainer, false);
            TextView title = row.findViewById(R.id.tvCameraName);
            TextView detail = row.findViewById(R.id.tvCameraDetail);
            Button star = row.findViewById(R.id.btnFavorite);
            title.setText(name(d));
            String battery = d.battery >= 0 && d.battery <= 100
                    ? d.battery + " %" : "non communiqué";
            String model = d.model.isEmpty() ? "Modèle non communiqué" : d.model;
            detail.setText("Batterie : " + battery + "   ·   " + model
                    + "\nÉtat renvoyé : " + d.state
                    + (d.firmware.isEmpty() ? "" : "   ·   Firmware " + d.firmware));
            if (d.battery >= 0 && d.battery < 20) {
                detail.setTextColor(Color.rgb(255, 170, 125));
            }
            star.setText(vault.isFavorite(d.id) ? "★" : "☆");
            star.setContentDescription(vault.isFavorite(d.id) ? "Retirer des favoris" : "Ajouter aux favoris");
            star.setOnClickListener(v -> {
                vault.setFavorite(d.id, !vault.isFavorite(d.id));
                renderDevices();
            });
            row.findViewById(R.id.btnHistory).setOnClickListener(v -> selectHistory(d));
            row.findViewById(R.id.btnLive).setOnClickListener(v -> openOfficial());
            row.findViewById(R.id.btnRename).setOnClickListener(v -> rename(d));
            deviceContainer.addView(row);
            visible++;
        }
        deviceTitle.setText("Mes caméras (" + visible + "/" + devices.size() + ")");
        if (devices.isEmpty()) showMessageIn(deviceContainer,
                "Aucune caméra trouvée, ou synchronisation non effectuée.");
        else if (visible == 0) showMessageIn(deviceContainer,
                "Aucune caméra ne correspond au filtre.");
    }

    private void rename(CloudApi.Device device) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(vault.label(device.id));
        int space = (int)(20 * getResources().getDisplayMetrics().density);
        field.setPadding(space, space, space, space);
        new AlertDialog.Builder(this)
                .setTitle("Nom personnalisé : " + device.name)
                .setView(field)
                .setNegativeButton("Annuler", null)
                .setNeutralButton("Nom d'origine", (d, w) -> {
                    vault.setLabel(device.id, "");
                    renderDevices();
                })
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String label = field.getText().toString().trim();
                    vault.setLabel(device.id, label.substring(0, Math.min(60, label.length())));
                    renderDevices();
                }).show();
    }

    private void selectHistory(CloudApi.Device device) {
        selectedDevice = device;
        historyPanel.setVisibility(View.VISIBLE);
        historyTitle.setText("Historique · " + name(device));
        loadHistory();
    }

    private void chooseDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDay.set(year, month, day);
            loadHistory();
        }, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH),
                selectedDay.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private String day() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(selectedDay.getTime());
    }

    private void loadHistory() {
        if (selectedDevice == null || sessionId == null) return;
        final String cameraId = selectedDevice.id;
        final String token = sessionId;
        final String date = day();
        dateButton.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                .format(selectedDay.getTime()));
        historyInfo.setText("Chargement des événements…");
        recordingContainer.removeAllViews();
        tasks.execute(() -> {
            try {
                List<CloudApi.Recording> found = cloud.listRecordings(token, cameraId, date);
                runOnUiThread(() -> {
                    if (selectedDevice == null || !cameraId.equals(selectedDevice.id)
                            || !token.equals(sessionId) || !date.equals(day())) return;
                    recordings.clear();
                    recordings.addAll(found);
                    renderRecordings();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    historyInfo.setText("Historique indisponible pour ce jour.");
                    info("Historique Aiwit", friendlyError(e)
                            + "\nL'accès peut dépendre du service cloud du fabricant.");
                });
            }
        });
    }

    private void renderRecordings() {
        recordingContainer.removeAllViews();
        historyInfo.setText(recordings.size() + " événement(s) le " + dateButton.getText()
                + " · les vidéos chiffrées ne sont pas lisibles ici");
        for (CloudApi.Recording recording : recordings) {
            View row = getLayoutInflater().inflate(R.layout.row_recording, recordingContainer, false);
            TextView title = row.findViewById(R.id.tvRecordingTime);
            TextView details = row.findViewById(R.id.tvRecordingDetail);
            title.setText(recording.time.isEmpty() ? "Événement" : recording.time);
            details.setText("Type : " + recording.kind + "   ·   Durée : "
                    + recording.duration + " s   ·   " + recording.size + " octets");
            row.setOnClickListener(v -> info("Événement Aiwit",
                    "Ce clip est référencé dans le cloud Aiwit. La lecture vidéo du format propriétaire chiffré n'est pas encore prise en charge.\n\nOuvre l'application Aiwit pour le visionner."));
            recordingContainer.addView(row);
        }
        if (recordings.isEmpty()) showMessageIn(recordingContainer,
                "Aucun événement disponible pour cette caméra et cette date.");
    }

    private void exportCsv() {
        if (selectedDevice == null || recordings.isEmpty()) {
            info("Export", "Sélectionne un historique avec au moins un événement.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "CHK-Cameras-" + day() + ".csv");
        startActivityForResult(intent, REQUEST_CSV);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UpdateManager.REQUEST_UNKNOWN_APPS) {
            updater.resumeInstall();
            return;
        }
        if (requestCode != REQUEST_CSV || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) throw new IllegalStateException("Fichier inaccessible");
            StringBuilder csv = new StringBuilder("date;camera;heure;type;duree_secondes;octets;fichier\n");
            for (CloudApi.Recording item : recordings) {
                csv.append(csv(day())).append(';').append(csv(name(selectedDevice))).append(';')
                        .append(csv(item.time)).append(';').append(csv(item.kind)).append(';')
                        .append(item.duration).append(';').append(item.size).append(';')
                        .append(csv(item.fileName)).append('\n');
            }
            stream.write(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Historique CSV enregistré", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            info("Export impossible", friendlyError(e));
        }
    }

    private static String csv(String s) {
        String value = s == null ? "" : s;
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-")
                || value.startsWith("@")) value = "'" + value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void openOfficial() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(OFFICIAL_APP);
        if (intent != null) startActivity(intent);
        else new AlertDialog.Builder(this)
                .setTitle("Direct vidéo Aiwit")
                .setMessage("Le flux P2P est propriétaire. Installe ou réactive l'application officielle Aiwit pour regarder et parler via tes caméras.")
                .setNegativeButton("Fermer", null)
                .setPositiveButton("Voir sur Play Store", (d, w) -> {
                    Intent store = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=" + OFFICIAL_APP));
                    startActivity(store);
                }).show();
    }

    private void showSettings() {
        String[] actions = { "Configurer le dépôt GitHub des mises à jour",
                "Vérifier une mise à jour maintenant",
                "Ouvrir l'application officielle Aiwit",
                "Déconnexion du compte Aiwit", "À propos et confidentialité" };
        new AlertDialog.Builder(this)
                .setTitle("Options CHK Caméras")
                .setItems(actions, (dialog, choice) -> {
                    switch (choice) {
                        case 0: configureRepo(); break;
                        case 1: updater.check(true); break;
                        case 2: openOfficial(); break;
                        case 3: logout(); break;
                        default: info("Confidentialité",
                                "Application personnelle sans publicités ni analyseurs de comportement.\n"
                                + "Connexion directe chiffrée vers le cloud Aiwit pour les métadonnées.\n"
                                + "Jeton de session sécurisé par Android Keystore (Android 6+).\n"
                                + "Mot de passe jamais enregistré.\n"
                                + "Aucun accès à la caméra ou au micro du téléphone.\n"
                                + "Actualisation uniquement lorsque l'application est ouverte.\n"
                                + "La vidéo P2P et l'interphone restent dans Aiwit.");
                    }
                }).show();
    }

    private void configureRepo() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("propriétaire/nom-du-depot");
        field.setText(vault.repository());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        field.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle("Dépôt GitHub de mises à jour")
                .setMessage("Indique le dépôt contenant les Releases signées de CHK Caméras. Exemple : Chasmet/Camera-maison. Le dépôt doit réellement exister et être accessible.")
                .setView(field)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String repo = field.getText().toString().trim();
                    if (!repo.isEmpty() && !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                        info("Dépôt invalide", "Format requis : propriétaire/nom-du-depot");
                        return;
                    }
                    vault.setRepository(repo);
                    vault.setLastUpdateCheck(0L);
                    Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void logout() {
        new AlertDialog.Builder(this).setTitle("Déconnexion")
                .setMessage("Déconnecter le compte sans effacer les favoris et noms personnalisés ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Déconnecter", (dialog, which) -> {
                    vault.clear();
                    sessionId = null;
                    user = "";
                    selectedDevice = null;
                    devices.clear();
                    recordings.clear();
                    historyPanel.setVisibility(View.GONE);
                    updateSessionUI();
                    showWelcome();
                }).show();
    }

    private void showMessageIn(LinearLayout container, String message) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(Color.rgb(181, 194, 213));
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        container.addView(text);
    }

    private void info(String title, String body) {
        if (!isFinishing()) new AlertDialog.Builder(this)
                .setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }

    private static String friendlyError(Exception e) {
        String detail = e.getMessage();
        return detail == null ? "Erreur réseau ou service indisponible."
                : detail.substring(0, Math.min(180, detail.length()));
    }
}
