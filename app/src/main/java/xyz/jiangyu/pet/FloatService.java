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
import java.util.ArrayList;

public class FloatService extends Service {
    private WindowManager windowManager;
    private ImageView imageView;
    private WindowManager.LayoutParams layoutParams;
    private ArrayList<Bitmap> frames = new ArrayList<>();
    private int frameW, frameH, cols = 8, rows = 9;
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
        
        try {
            Bitmap sheet = BitmapFactory.decodeStream(getAssets().open("spritesheet.png"));
            if (sheet != null) {
                frameW = sheet.getWidth() / cols;
                frameH = sheet.getHeight() / rows;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        Bitmap frame = Bitmap.createBitmap(sheet, c * frameW, r * frameH, frameW, frameH);
                        frames.add(frame);
                    }
                }
                sheet.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!frames.isEmpty()) {
            imageView.setImageBitmap(frames.get(0));
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
        
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        int size = dpToPx(100);
        layoutParams = new WindowManager.LayoutParams(
            size, size, type,
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
    
    private void showFrame(int row, int col) {
        int idx = row * cols + col;
        if (idx >= 0 && idx < frames.size()) {
            imageView.setImageBitmap(frames.get(idx));
        }
    }
    
    private void playRow(int row, int speed, boolean loop) {
        if (animRunnable != null) handler.removeCallbacks(animRunnable);
        showFrame(row, 0);
        animRunnable = new Runnable() {
            int c = 0;
            public void run() {
                c++;
                if (c >= cols) {
                    if (loop) { c = 0; showFrame(row, 0); }
                    else { showFrame(row, cols - 1); idle(); return; }
                } else {
                    showFrame(row, c);
                }
                handler.postDelayed(this, speed);
            }
        };
        handler.postDelayed(animRunnable, speed);
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
            NotificationChannel channel = new NotificationChannel(channelId, "\u684c\u5ba0", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, channelId)
            : new Notification.Builder(this);
        builder.setContentTitle("\u6708\u85aa\u55b5")
                .setContentText("\u8e72\u5728\u5c4f\u5e55\u89d2\u843d")
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
        for (Bitmap b : frames) { if (b != null && !b.isRecycled()) b.recycle(); }
        frames.clear();
        super.onDestroy();
    }
}
