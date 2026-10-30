package com.ethanconnect.tv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Launcher settings: the paired PC, system settings and a way back to the TV's original home screen. */
public class SettingsActivity extends AppCompatActivity {
    /** Stock TV home apps tried when no other HOME activity is registered. */
    private static final String[] STOCK_HOMES = {
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.mitv.tvhome.atv",
            "com.sony.dtv.sonysystemservice", "com.tcl.tvlauncher", "com.hisense.launcher"};

    private PcStore store;
    private TextView pairedValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        store = new PcStore(this);
        pairedValue = findViewById(R.id.pairedValue);

        findViewById(R.id.btnPair).setOnClickListener(v ->
                PairDialog.show(this, store.host().isEmpty() ? "" : store.address(), this::refresh));
        findViewById(R.id.btnForget).setOnClickListener(v -> {
            store.forget();
            refresh();
        });
        findViewById(R.id.btnWallpaper).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperActivity.class)));
        findViewById(R.id.btnScreensaver).setOnClickListener(v ->
                startActivity(new Intent(this, WallpaperActivity.class)
                        .putExtra(WallpaperActivity.EXTRA_SCREENSAVER, true)));
        findViewById(R.id.btnTvSettings).setOnClickListener(v -> openTvSettings());
        findViewById(R.id.btnStockHome).setOnClickListener(v -> openStockHome());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPair).requestFocus();
        refresh();
    }

    private void refresh() {
        pairedValue.setText(store.isPaired() ? store.address() : getString(R.string.not_paired));
    }

    private void openTvSettings() {
        for (String action : new String[]{"com.android.tv.settings.MAIN_SETTINGS", Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, getString(R.string.cant_open, getString(R.string.settings)), Toast.LENGTH_SHORT).show();
    }

    /** Opens the TV's original home. Home on the remote still returns to EthanConnect while it is default. */
    private void openStockHome() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> homes = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        for (ResolveInfo ri : homes) {
            if (!getPackageName().equals(ri.activityInfo.packageName)
                    && !"com.android.tv.settings".equals(ri.activityInfo.packageName)) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        for (String pkg : STOCK_HOMES) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        Toast.makeText(this, getString(R.string.cant_open, getString(R.string.open_stock_home)), Toast.LENGTH_SHORT).show();
    }
}
