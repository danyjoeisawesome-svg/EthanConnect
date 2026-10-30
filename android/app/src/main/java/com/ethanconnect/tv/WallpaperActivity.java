package com.ethanconnect.tv;

import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Lets the viewer choose a bundled background for the launcher. */
public class WallpaperActivity extends AppCompatActivity {
    public static final String EXTRA_SCREENSAVER = "screensaver_mode";
    private static final String PREFS = "home_appearance";
    private boolean screensaverMode;
    private String preferenceKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallpapers);

        screensaverMode = getIntent().getBooleanExtra(EXTRA_SCREENSAVER, false);
        preferenceKey = screensaverMode ? "screensaver" : "wallpaper";
        TextView title = findViewById(R.id.wallpaperScreenTitle);
        TextView hint = findViewById(R.id.wallpaperScreenHint);
        title.setText(screensaverMode ? R.string.screensaver_title : R.string.wallpaper_title);
        hint.setText(screensaverMode ? R.string.screensaver_hint : R.string.wallpaper_hint);

        String selected = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(preferenceKey, "midnight");
        LinearLayout options = findViewById(R.id.wallpaperOptions);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String key : getResources().getStringArray(R.array.wallpaper_options)) {
            View card = inflater.inflate(R.layout.item_wallpaper, options, false);
            int labelId = getResources().getIdentifier("wallpaper_name_" + key, "string", getPackageName());
            int imageId = getResources().getIdentifier("wallpaper_" + key, "drawable", getPackageName());
            TextView label = card.findViewById(R.id.title);
            ImageView preview = card.findViewById(R.id.preview);
            TextView badge = card.findViewById(R.id.selected);
            label.setText(labelId == 0 ? key : getString(labelId));
            card.setContentDescription(label.getText());
            if ("midnight".equals(key) || imageId == 0) {
                preview.setBackgroundResource(R.drawable.home_background);
            } else {
                preview.setImageResource(imageId);
            }
            badge.setVisibility(key.equals(selected) ? View.VISIBLE : View.GONE);
            card.setOnClickListener(v -> select(key));
            options.addView(card);
        }

        View settingsButton = findViewById(R.id.btnDreamSettings);
        if (screensaverMode) {
            settingsButton.setVisibility(View.VISIBLE);
            settingsButton.setOnClickListener(v -> openDreamSettings());
        }
        if (options.getChildCount() > 0) options.getChildAt(0).requestFocus();
    }

    private void select(String wallpaper) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(preferenceKey, wallpaper)
                .apply();
        Toast.makeText(this, screensaverMode ? R.string.screensaver_saved : R.string.wallpaper_saved,
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private void openDreamSettings() {
        Intent intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
        try {
            if (intent.resolveActivity(getPackageManager()) == null) {
                intent = new Intent(Settings.ACTION_SETTINGS);
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_open, getString(R.string.screensaver_name)),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
