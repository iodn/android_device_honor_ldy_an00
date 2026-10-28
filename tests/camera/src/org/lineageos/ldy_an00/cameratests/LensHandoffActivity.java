// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.cameratests;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;

/** Keeps camera testing visible and subject to ordinary foreground-app restrictions. */
public final class LensHandoffActivity extends Activity {
    final CountDownLatch resumed = new CountDownLatch(1);
    volatile boolean foreground;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text = new TextView(this);
        text.setText("Camera lens test\nCamera capture is active. Images are discarded.");
        text.setPadding(32, 64, 32, 32);
        setContentView(text);
    }
    @Override public void onResume() {
        super.onResume();
        foreground = true;
        resumed.countDown();
    }
    @Override public void onPause() {
        foreground = false;
        super.onPause();
    }
}
