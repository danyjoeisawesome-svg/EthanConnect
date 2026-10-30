package com.ethanconnect.tv;

import android.animation.ValueAnimator;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/** Android TV screensaver that shows the viewer's selected image with gentle motion. */
public class EthanDreamService extends DreamService {
    private ImageView wallpaper;
    private View clockGroup;
    private ValueAnimator motion;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_screensaver);

        wallpaper = findViewById(R.id.dreamWallpaper);
        clockGroup = findViewById(R.id.dreamClockGroup);
        String choice = getSharedPreferences("home_appearance", MODE_PRIVATE)
                .getString("screensaver", "midnight");
        int imageId = getResources().getIdentifier("wallpaper_" + choice, "drawable", getPackageName());
        if (!"midnight".equals(choice) && imageId != 0) {
            wallpaper.setImageResource(imageId);
            wallpaper.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        float density = getResources().getDisplayMetrics().density;
        motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setDuration(60000);
        motion.setRepeatCount(ValueAnimator.INFINITE);
        motion.setRepeatMode(ValueAnimator.REVERSE);
        motion.setInterpolator(new LinearInterpolator());
        motion.addUpdateListener(animator -> {
            float progress = (float) animator.getAnimatedValue();
            float drift = (progress * 2f - 1f) * 20f * density;
            if (wallpaper != null && wallpaper.getVisibility() == View.VISIBLE) {
                wallpaper.setTranslationX(drift);
                wallpaper.setTranslationY(-drift * 0.45f);
                wallpaper.setScaleX(1.05f + progress * 0.025f);
                wallpaper.setScaleY(1.05f + progress * 0.025f);
            }
            if (clockGroup != null) {
                clockGroup.setTranslationX(-drift * 0.5f);
                clockGroup.setTranslationY(drift * 0.2f);
            }
        });
        motion.start();
    }

    @Override
    public void onDreamingStopped() {
        if (motion != null) {
            motion.cancel();
            motion = null;
        }
        super.onDreamingStopped();
    }
}
