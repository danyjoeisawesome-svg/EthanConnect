package com.ethanconnect.tv;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One tile in the home grid. */
public class AppEntry {
    public static final int APP = 0;
    public static final int MIRROR = 1;
    public static final int HOME_SETTINGS = 2;
    public static final int SETTINGS = 3;

    public final int kind;
    public final String label;
    public final ComponentName component;
    public final String pkg;
    public final Drawable banner;
    public final Drawable icon;
    public final String name; // label + optional caption for display under the tile

    public AppEntry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String name) {
        this.kind = kind;
        this.label = label;
        this.component = component;
        this.pkg = component == null ? null : component.getPackageName();
        this.banner = banner;
        this.icon = icon;
        this.name = name;
    }

    public boolean isMirror() { return kind == MIRROR; }
    public boolean isHomeSettings() { return kind == HOME_SETTINGS; }
    public boolean isSettings() { return kind == SETTINGS; }
}