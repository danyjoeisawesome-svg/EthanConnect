package com.ethanconnect.tv;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads the launcher configuration from res/values/config.xml (and, at runtime,
 * an optional theme config.json pushed into the app's private files).
 */
public class Config {
    public final Context ctx;
    public final Resources res;

    public final List<String> dockApps;
    public final boolean fillDock;
    public final Set<String> hiddenApps;
    public final Map<String, String> launchActions;
    public final Map<String, String> appLabels;
    public final boolean showHomeSettingsShortcut;
    public final boolean appLibrary;
    public final int columns;
    public final boolean animations;
    public final boolean advancedEffects;
    public final boolean reducedMotion;
    public final int homeIdleClockMinutes;

    public Config(Context ctx, Resources res) {
        this.ctx = ctx;
        this.res = res;

        dockApps = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.dock_apps)));
        fillDock = res.getBoolean(R.bool.fill_dock);
        hiddenApps = new HashSet<>(Arrays.asList(res.getStringArray(R.array.hidden_apps)));

        launchActions = new TreeMap<>();
        for (String entry : res.getStringArray(R.array.launch_actions)) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) launchActions.put(parts[0], parts[1]);
        }

        appLabels = new TreeMap<>();
        for (String entry : res.getStringArray(R.array.app_labels)) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) appLabels.put(parts[0], parts[1]);
        }

        showHomeSettingsShortcut = false;
        appLibrary = res.getBoolean(R.bool.app_library);
        columns = res.getInteger(R.integer.columns);
        animations = res.getBoolean(R.bool.animations);
        advancedEffects = animations && res.getBoolean(R.bool.advanced_effects);
        reducedMotion = res.getBoolean(R.bool.reduced_motion);
        homeIdleClockMinutes = res.getInteger(R.integer.home_idle_clock_minutes);
    }

    /** Tile width for a grid of this inner width. */
    public static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(180f * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / 1.10f);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    public static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * 101f / 180f);
    }

    public static float density(Context ctx) {
        return ctx.getResources().getDisplayMetrics().density;
    }
}