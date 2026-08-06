package xyz.jiangyu.pet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {
    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!started) checkAndStart();
    }

    private void checkAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                ));
                return;
            }
        }
        started = true;
        startService(new Intent(this, FloatService.class));
        finish();
    }
}
