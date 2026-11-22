package com.chk.cameras;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

/** Affichage natif DANS l'application, aucun Intent vers le fabricant. */
@OptIn(markerClass = UnstableApi.class)
public final class LiveActivity extends Activity {
    public static final String EXTRA_PROFILE_ID = "profileId";
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private StreamStore.Profile profile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live);
        playerView = findViewById(R.id.livePlayerView);
        status = findViewById(R.id.liveStatus);
        String id = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        profile = id == null ? null : new StreamStore(this).get(id);
        findViewById(R.id.btnLiveBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnLiveReconnect).setOnClickListener(v -> startPlayback());
        if (profile == null || !StreamStore.isSupported(profile.uri)) {
            status.setText("Aucun flux compatible n'est configuré pour cette caméra.");
            findViewById(R.id.btnLiveReconnect).setEnabled(false);
        } else {
            ((TextView) findViewById(R.id.liveCameraName)).setText(profile.name);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (profile != null) startPlayback();
    }

    private void startPlayback() {
        if (profile == null) return;
        releasePlayer();
        status.setVisibility(View.VISIBLE);
        status.setText("Connexion au flux vidéo…");
        try {
            Uri uri = Uri.parse(profile.uri);
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) status.setVisibility(View.GONE);
                    else if (state == Player.STATE_BUFFERING) {
                        status.setVisibility(View.VISIBLE);
                        status.setText("Chargement du direct…");
                    } else if (state == Player.STATE_ENDED) {
                        status.setVisibility(View.VISIBLE);
                        status.setText("Le flux s'est terminé. Appuie sur Reconnecter.");
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    status.setVisibility(View.VISIBLE);
                    status.setText("Connexion vidéo impossible. Vérifie que la caméra est allumée, "
                            + "que le flux est autorisé et que le réseau est accessible. "
                            + "Appuie sur Reconnecter.");
                }
            });
            MediaItem item = MediaItem.fromUri(uri);
            String protocol = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ("rtsp".equals(protocol)) {
                player.setMediaSource(new RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true).setTimeoutMs(12000).createMediaSource(item));
            } else if (uri.getPath() != null && uri.getPath().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".m3u8")) {
                player.setMediaSource(new HlsMediaSource.Factory(new DefaultHttpDataSource.Factory())
                        .createMediaSource(item));
            } else {
                player.setMediaItem(item); // MP4 / autres formats HTTP que Media3 reconnaît
            }
            player.prepare();
            player.play();
        } catch (RuntimeException ex) {
            status.setVisibility(View.VISIBLE);
            status.setText("Source non prise en charge. Format attendu : RTSP ou HLS.");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @Override protected void onStop() {
        releasePlayer();
        super.onStop();
    }
}
