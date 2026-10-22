package com.ethanconnect.tv;

import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Lists the TV's HDMI / AV (passthrough) inputs and switches to the chosen one. */
public class InputActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inputs);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        LinearLayout list = findViewById(R.id.inputsList);
        TvInputManager tv = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = tv == null ? null : tv.getTvInputList();
        View first = null;
        if (inputs != null) {
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence label = info.loadLabel(this);
                if (info.loadCustomLabel(this) != null) label = info.loadCustomLabel(this);
                TextView b = new TextView(this, null, 0, R.style.TopButton);
                b.setText(label);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Math.round(12 * getResources().getDisplayMetrics().density);
                b.setLayoutParams(lp);
                b.setOnClickListener(v -> switchTo(info));
                list.addView(b);
                if (first == null) first = b;
            }
        }
        if (first == null) findViewById(R.id.inputsEmpty).setVisibility(View.VISIBLE);
        else first.requestFocus();
    }

    private void switchTo(TvInputInfo info) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        } catch (Exception e) {
            findViewById(R.id.inputsEmpty).setVisibility(View.VISIBLE);
        }
    }
}
