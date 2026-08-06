package xyz.jiangyu.pet;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class FloatService extends Service {
    private WindowManager windowManager;
    private ImageView imageView;
    private WindowManager.LayoutParams layoutParams;
    private Bitmap spritesheet;
    private int frameW = 192, frameH = 208;
    private int cols = 8, rows = 9;
    private int curRow = 0, curCol = 0;
    private Handler handler = new Handler();
    private Runnable animRunnable;
    private long lastTap = 0, touchStart = 0;
    private float initX, initY, initTouchX, initTouchY;
    private boolean hasMoved = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Load spritesheet from assets
        try {
            spritesheet = BitmapFactory.decodeStream(getAssets().open("spritesheet.png"));
            if (spritesheet != null) {
                frameW = spritesheet.getWidth() / cols;
                frameH = spritesheet.getHeight() / rows;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        imageView = new ImageView(this);
        if (spritesheet != null) {
            setFrame(0, 0);
        }
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = layoutParams.x;
                        initY = layoutParams.y;
                        initTouchX = event.getRawX();
                        initTouchY = event.getRawY();
                        touchStart = System.currentTimeMillis();
                        hasMoved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initTouchX;
                        float dy = event.getRawY() - initTouchY;
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) hasMoved = true;
                        layoutParams.x = (int)(initX + dx);
                        layoutParams.y = (int)(initY + dy);
                        windowManager.updateViewLayout(imageView, layoutParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long dt = System.currentTimeMillis() - touchStart;
                        if (hasMoved) return true;
                        if (dt < 300) {
                            long since = System.currentTimeMillis() - lastTap;
                            if (since < 400) { playRow(6, 80, false); showToast("喵喵喵~"); }
                            else if (since < 800) { playRow(4, 100, false); showToast("嗷！"); }
                            else { playRow(2, 120, false); showToast("喵~"); }
                            lastTap = System.currentTimeMillis();
                        } else if (dt > 600) {
                            playRow(8, 100, false);
                            showToast("呼噜噜...");
                        }
                        return true;
                }
                return false;
            }
        });
        
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        int size = dpToPx(150);
        layoutParams = new WindowManager.LayoutParams(
            size, size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 100;
        layoutParams.y = 400;
        windowManager.addView(imageView, layoutParams);
        
        idle();
    }
    
    private void setFrame(int row, int col) {
        if (spritesheet == null) return;
        curRow = row; curCol = col;
        int x = col * frameW;
        int y = row * frameH;
        Bitmap frame = Bitmap.createBitmap(spritesheet, x, y, frameW, frameH);
        imageView.setImageBitmap(frame);
    }
    
    private void playRow(int row, int speed, boolean loop) {
        if (animRunnable != null) handler.removeCallbacks(animRunnable);
        curCol = 0;
        setFrame(row, 0);
        final int r = row;
        final int sp = speed;
        final boolean lp = loop;
        animRunnable = new Runnable() {
            int c = 0;
            @Override
            public void run() {
                c++;
                if (c >= cols) {
                    if (lp) { c = 0; setFrame(r, 0); }
                    else { c = cols - 1; idle(); return; }
                } else {
                    setFrame(r, c);
                }
                handler.postDelayed(this, sp);
            }
        };
        handler.postDelayed(animRunnable, sp);
    }
    
    private void idle() {
        playRow(0, 150, true);
    }
    
    private void showToast(String text) {
        Toast.makeText(FloatService.this, text, Toast.LENGTH_SHORT).show();
    }
    
    private int dpToPx(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
    
    private void startForegroundNotification() {
        String channelId = "pet_overlay";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "桌宠", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
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
    public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public void onDestroy() {
        if (handler != null && animRunnable != null) handler.removeCallbacks(animRunnable);
        if (windowManager != null && imageView != null) windowManager.removeView(imageView);
        super.onDestroy();
    }
}
