package xyz.jiangyu.pet;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class AppWatchService extends AccessibilityService {
    public static volatile String currentPackage = null;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : null;
            if (pkg != null && !pkg.equals("xyz.jiangyu.pet")) {
                currentPackage = pkg;
            }
        }
    }
    
    @Override
    public void onInterrupt() {}
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        android.util.Log.d("AppWatch", "AccessibilityService connected");
    }
}
