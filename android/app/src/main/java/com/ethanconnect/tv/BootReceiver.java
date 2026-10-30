package com.ethanconnect.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Restores EthanConnect after TV firmware that directly launches its vendor splash activity at boot. */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "EthanConnect";
    // Xiaomi firmware may start PatchWall after Android has already resolved HOME.
    // Retry briefly so a late vendor splash cannot permanently cover EthanConnect.
    private static final long[] BOOT_LAUNCH_DELAYS_MS = {2000L, 15000L, 45000L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final Context appContext = context.getApplicationContext();
        Log.i(TAG, "Received BOOT_COMPLETED; scheduling home launch attempts");
        final PendingResult pending = goAsync();
        Handler handler = new Handler(Looper.getMainLooper());
        for (int attempt = 0; attempt < BOOT_LAUNCH_DELAYS_MS.length; attempt++) {
            final int attemptNumber = attempt + 1;
            final boolean finishBroadcast = attempt == 0;
            handler.postDelayed(() -> {
                try {
                    Intent launch = new Intent(appContext, MainActivity.class);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    appContext.startActivity(launch);
                    Log.i(TAG, "Boot home launch attempt " + attemptNumber + " sent");
                } catch (RuntimeException e) {
                    Log.w(TAG, "Boot home launch attempt " + attemptNumber + " failed", e);
                } finally {
                    if (finishBroadcast) pending.finish();
                }
            }, BOOT_LAUNCH_DELAYS_MS[attempt]);
        }
    }
}
