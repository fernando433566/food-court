package es.foodcourt;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

/**
 * Invisible activity that turns the sender's screen on and dismisses a keyguard without
 * PIN, pattern or password, so the official app can be opened for a paired receiver.
 * A secure keyguard is never bypassed: Android keeps asking for the credential.
 */
public final class WakeActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (Build.VERSION.SDK_INT >= 26 && keyguard != null && keyguard.isKeyguardLocked()
                && !keyguard.isKeyguardSecure()) {
            keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { done(); }
                @Override public void onDismissCancelled() { done(); }
                @Override public void onDismissError() { done(); }
            });
            getWindow().getDecorView().postDelayed(this::done, 3000);
        } else {
            getWindow().getDecorView().postDelayed(this::done, 600);
        }
    }

    private void done() {
        if (!isFinishing()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
