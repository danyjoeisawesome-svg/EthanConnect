package com.ethanconnect.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** The one paired PC: address, pinned certificate fingerprint and access token. */
final class PcStore {
    private static final String PREFS = "ethanconnect";
    private final SharedPreferences prefs;

    PcStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String host() { return prefs.getString("host", ""); }
    int port() { return prefs.getInt("port", 8443); }
    String fingerprint() { return prefs.getString("fp", null); }
    String token() { return prefs.getString("token", null); }
    boolean isPaired() { return token() != null && fingerprint() != null && !host().isEmpty(); }
    String address() { return host() + ":" + port(); }

    void savePaired(String host, int port, String fingerprint, String token) {
        prefs.edit().putString("host", host).putInt("port", port)
                .putString("fp", fingerprint).putString("token", token).apply();
    }

    void updateAddress(String host, int port) {
        prefs.edit().putString("host", host).putInt("port", port).apply();
    }

    void forget() {
        prefs.edit().remove("token").remove("fp").apply();
    }
}
