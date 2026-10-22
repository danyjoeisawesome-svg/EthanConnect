package com.ethanconnect.tv;

import android.app.Activity;
import android.os.Build;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/** Pairs this TV with the EthanConnect PC app: PC address plus the PIN shown on the PC. */
final class PairDialog {
    private PairDialog() {}

    static void show(Activity activity, String prefillAddress, Runnable onPaired) {
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView hint = new TextView(activity);
        hint.setText(R.string.pair_hint);
        box.addView(hint, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText address = new EditText(activity);
        address.setHint(R.string.pair_address_hint);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(prefillAddress);
        address.setSingleLine();
        address.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        box.addView(address);

        EditText pin = new EditText(activity);
        pin.setHint(R.string.pair_pin_hint);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER);
        pin.setSingleLine();
        pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
        box.addView(pin);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.pair_title)
                .setView(box)
                .setPositiveButton(R.string.pair_button, (d, w) -> {
                    String a = address.getText().toString().trim();
                    String p = pin.getText().toString().replaceAll("\\s", "");
                    if (!a.matches("[^:\\s]+(:\\d{1,5})?") || !p.matches("\\d{6}")) {
                        Toast.makeText(activity, R.string.pair_invalid, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String host = a.contains(":") ? a.substring(0, a.indexOf(':')) : a;
                    int port = a.contains(":") ? Integer.parseInt(a.substring(a.indexOf(':') + 1)) : 8443;
                    Toast.makeText(activity, R.string.pair_working, Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        try {
                            String[] r = new PcClient(host, port, null).pair(p, Build.MODEL);
                            new PcStore(activity).savePaired(host, port, r[1], r[0]);
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, R.string.pair_ok, Toast.LENGTH_SHORT).show();
                                if (onPaired != null) onPaired.run();
                            });
                        } catch (Exception e) {
                            android.util.Log.w("EthanConnect", "pairing failed", e);
                            activity.runOnUiThread(() -> Toast.makeText(activity,
                                    activity.getString(R.string.pair_failed), Toast.LENGTH_LONG).show());
                        }
                    }, "pair").start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        pin.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); // Done on the keyboard = Pair
            return true;
        });
        dialog.show();
    }
}
