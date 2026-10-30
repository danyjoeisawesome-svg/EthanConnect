package com.ethanconnect.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Main home screen: a D-pad navigable grid of app tiles with a dock, Mirror tile,
 * Settings and Inputs buttons along the top.
 */
public class MainActivity extends AppCompatActivity implements AppAdapter.TileListener {

    private Config config;
    private AppAdapter adapter;
    private RecyclerView grid;
    private LinearLayout topBar;
    private TextView btnMirror;
    private TextView btnSettings;
    private TextView btnInputs;
    private ImageView homeWallpaper;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable clockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        homeWallpaper = findViewById(R.id.homeWallpaper);
        applyHomeWallpaper();

        config = new Config(this, getResources());

        grid = findViewById(R.id.grid);
        topBar = findViewById(R.id.topButtons);
        btnMirror = findViewById(R.id.btnMirror);
        btnSettings = findViewById(R.id.btnSettings);
        btnInputs = findViewById(R.id.btnInputs);

        adapter = new AppAdapter(this, config, this);
        int columns = config.columns;
        GridLayoutManager layoutManager = new GridLayoutManager(this, columns);
        grid.setLayoutManager(layoutManager);
        grid.setAdapter(adapter);
        grid.setHasFixedSize(false);

        // Top bar buttons
        btnMirror.setOnClickListener(v -> {
            Intent i = new Intent(this, MirrorActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        btnSettings.setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        btnInputs.setOnClickListener(v -> {
            Intent i = new Intent(this, InputActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

        // Keep screen on while on home
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Load apps
        loadApps();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanback = pm.queryIntentActivities(leanbackIntent, 0);
        adapter.setLeanbackPackages(toPackageSet(leanback));
        adapter.reload(leanback);
    }

    private Set<String> toPackageSet(List<ResolveInfo> list) {
        Set<String> set = new TreeSet<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null) set.add(ri.activityInfo.packageName);
        }
        return set;
    }

    @Override
    public void onTileFocused(int position, View card) {
        // Optional: highlight focused tile
    }

    @Override
    public void onTileClicked(AppEntry entry) {
        adapter.launch(entry);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyHomeWallpaper();
        loadApps();
        startClock();
    }

    private void applyHomeWallpaper() {
        if (homeWallpaper == null) return;
        String choice = getSharedPreferences("home_appearance", MODE_PRIVATE)
                .getString("wallpaper", "midnight");
        int imageId = getResources().getIdentifier("wallpaper_" + choice, "drawable", getPackageName());
        if (!"midnight".equals(choice) && imageId != 0) {
            homeWallpaper.setImageResource(imageId);
            homeWallpaper.setVisibility(View.VISIBLE);
        } else {
            homeWallpaper.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopClock();
    }

    private void startClock() {
        stopClock();
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                TextView clock = findViewById(R.id.clock);
                if (clock != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
                    clock.setText(sdf.format(new java.util.Date()));
                }
                handler.postDelayed(this, 60000);
            }
        };
        handler.post(clockRunnable);
    }

    private void stopClock() {
        if (clockRunnable != null) handler.removeCallbacks(clockRunnable);
        clockRunnable = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Let D-pad navigation flow through to the grid
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Home screen: do nothing on back
    }
}
