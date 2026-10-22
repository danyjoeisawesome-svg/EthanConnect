package com.ethanconnect.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

/**
 * Shows the PC screen streamed by the EthanConnect PC app and forwards remote keys to it.
 * While the picture is up every key goes to the PC, except Back (leave) and Menu (re-pair).
 */
public class MirrorActivity extends AppCompatActivity {
    private static final String BEACON_MAGIC = "ETHANCONNECT1";

    private SurfaceView surface;
    private LinearLayout overlay;
    private ProgressBar progress;
    private TextView status;
    private View pairButton;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService keySender = Executors.newSingleThreadExecutor();
    private PcStore store;
    private volatile boolean running;
    private volatile boolean showing;           // a frame has been drawn since the last (re)connect
    private volatile String discoveredAddress = "";
    private Thread streamThread;
    private Thread discoveryThread;
    private volatile HttpsURLConnection activeConnection;
    private PcClient keyClient;
    private String keyClientId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        store = new PcStore(this);

        surface = findViewById(R.id.mirrorSurface);
        overlay = findViewById(R.id.mirrorOverlay);
        progress = findViewById(R.id.mirrorProgress);
        status = findViewById(R.id.mirrorStatus);
        pairButton = findViewById(R.id.mirrorChangeHost);
        pairButton.setOnClickListener(v -> pair());
        pairButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        startDiscovery();
        if (store.isPaired()) {
            startStream();
        } else {
            showStatus(getString(R.string.mirror_not_paired), false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        stopStream();
        if (discoveryThread != null) discoveryThread.interrupt();
        discoveryThread = null;
    }

    @Override
    protected void onDestroy() {
        keySender.shutdownNow();
        super.onDestroy();
    }

    private void pair() {
        String prefill = store.host().isEmpty() ? discoveredAddress : store.address();
        PairDialog.show(this, prefill, () -> {
            stopStream();
            if (running) startStream();
        });
    }

    private void showStatus(String text, boolean busy) {
        ui.post(() -> {
            status.setText(text);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            overlay.setVisibility(View.VISIBLE);
            showing = false;
            pairButton.requestFocus();
        });
    }

    // ------------------------------------------------------------------ video

    private synchronized void startStream() {
        if (streamThread != null) return;
        streamThread = new Thread(this::streamLoop, "pc-stream");
        streamThread.start();
    }

    private synchronized void stopStream() {
        if (streamThread != null) streamThread.interrupt();
        HttpsURLConnection c = activeConnection;
        if (c != null) c.disconnect(); // unblocks a read in progress
        streamThread = null;
    }

    private void streamLoop() {
        Thread me = Thread.currentThread();
        while (running && !me.isInterrupted()) {
            String host = store.host();
            int port = store.port();
            String token = store.token();
            if (token == null) break;
            showStatus(getString(R.string.connecting, host + ":" + port), true);
            try {
                HttpsURLConnection conn = new PcClient(host, port, store.fingerprint()).openStream(token);
                activeConnection = conn;
                try (InputStream in = conn.getInputStream()) {
                    while (running && !me.isInterrupted()) {
                        byte[] jpeg = PcClient.readFrame(in);
                        Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                        if (bmp != null) draw(bmp);
                    }
                }
            } catch (PcClient.Unauthorized e) {
                store.forget();
                showStatus(getString(R.string.mirror_unpaired), false);
                break;
            } catch (Exception e) {
                if (!running || me.isInterrupted()) break;
                showStatus(getString(R.string.mirror_unreachable, host + ":" + port), true);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    break;
                }
            } finally {
                activeConnection = null;
            }
        }
        synchronized (this) {
            if (streamThread == me) streamThread = null;
        }
    }

    private void draw(Bitmap bmp) {
        SurfaceHolder holder = surface.getHolder();
        Canvas canvas = holder.getSurface().isValid() ? holder.lockCanvas() : null;
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.BLACK);
            float scale = Math.min((float) canvas.getWidth() / bmp.getWidth(), (float) canvas.getHeight() / bmp.getHeight());
            int w = Math.round(bmp.getWidth() * scale), h = Math.round(bmp.getHeight() * scale);
            int left = (canvas.getWidth() - w) / 2, top = (canvas.getHeight() - h) / 2;
            canvas.drawBitmap(bmp, null, new Rect(left, top, left + w, top + h), null);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
        if (!showing) {
            showing = true;
            ui.post(() -> overlay.setVisibility(View.GONE));
        }
    }

    // ------------------------------------------------------------------ discovery

    /** The PC broadcasts "ETHANCONNECT1|ip|port|fingerprint16|name" on UDP 8082. Only a PC whose certificate
     * matches the paired one is followed; otherwise the address is just offered as a pairing default. */
    private void startDiscovery() {
        if (discoveryThread != null) return;
        discoveryThread = new Thread(() -> {
            WifiManager.MulticastLock lock = null;
            try (DatagramSocket socket = new DatagramSocket(null)) {
                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi != null) {
                    lock = wifi.createMulticastLock("ethanconnect-discovery");
                    lock.acquire();
                }
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(8082));
                byte[] buf = new byte[256];
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String[] f = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim().split("\\|");
                    if (f.length < 4 || !BEACON_MAGIC.equals(f[0])) continue;
                    int port;
                    try {
                        port = Integer.parseInt(f[2]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    discoveredAddress = f[1] + ":" + port;
                    String fp = store.fingerprint();
                    if (fp != null && fp.startsWith(f[3]) && (!f[1].equals(store.host()) || port != store.port())) {
                        store.updateAddress(f[1], port);
                        ui.post(() -> {
                            stopStream();
                            if (running) startStream();
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (lock != null && lock.isHeld()) lock.release();
            }
        }, "pc-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    // ------------------------------------------------------------------ remote keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!showing) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (!down) pair();
            return true;
        }
        String token = store.token();
        if (token != null) {
            String id = store.address() + store.fingerprint();
            if (!id.equals(keyClientId)) { // one client (and TLS context) reused across key presses
                keyClient = new PcClient(store.host(), store.port(), store.fingerprint());
                keyClientId = id;
            }
            PcClient client = keyClient;
            keySender.execute(() -> {
                try {
                    client.sendKey(token, code, down);
                } catch (Exception ignored) {
                }
            });
        }
        return true;
    }
}
