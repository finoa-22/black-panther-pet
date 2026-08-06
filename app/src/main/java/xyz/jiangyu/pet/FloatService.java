package xyz.jiangyu.pet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

public class FloatService extends Service {

    private WindowManager windowManager;
    private WebView webView;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setUseWideViewPort(false);
        webView.getSettings().setLoadWithOverviewMode(false);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(0x00000000);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new WebViewClient());
        try {
            java.io.InputStream is = getAssets().open("pet.html");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String html = new String(buf);
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            webView.loadUrl("file:///android_asset/pet.html");
        }

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        int size = dpToPx(150);
        layoutParams = new WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 200;
        layoutParams.y = 500;

        webView.setOnTouchListener(createTouchListener());
        windowManager.addView(webView, layoutParams);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private long touchStartTime, lastTapTime;
    private boolean hasMoved;

    private View.OnTouchListener createTouchListener() {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        hasMoved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            hasMoved = true;
                            layoutParams.x = initialX + dx;
                            layoutParams.y = initialY + dy;
                            windowManager.updateViewLayout(webView, layoutParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!hasMoved) {
                            long elapsed = System.currentTimeMillis() - touchStartTime;
                            long sinceLast = System.currentTimeMillis() - lastTapTime;
                            if (elapsed > 600) {
                                webView.evaluateJavascript("petAPI.showAngry();petAPI.say('...');", null);
                            } else if (sinceLast < 350) {
                                webView.evaluateJavascript("petAPI.showHappy();petAPI.say('喵~');", null);
                                lastTapTime = 0;
                            } else {
                                lastTapTime = System.currentTimeMillis();
                                webView.evaluateJavascript("petAPI.showSurprised();petAPI.say('!');", null);
                            }
                        }
                        return true;
                }
                return false;
            }
        };
    }

    private void startForegroundNotification() {
        String channelId = "pet_overlay";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "月薪喵",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle("月薪喵")
                .setContentText("蹲在屏幕角落")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true);
                

        startForeground(1, builder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            windowManager.removeView(webView);
        }
        super.onDestroy();
    }
}
