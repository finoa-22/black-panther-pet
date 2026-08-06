package xyz.jiangyu.pet;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.ArrayList;

public class FloatService extends Service {
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private WindowManager.LayoutParams layoutParams;
    private ArrayList<Bitmap> frames = new ArrayList<>();
    private int frameW, frameH, cols = 8, rows = 9;
    private int curFrameIdx = 0;
    private volatile boolean running = true;
    private Thread renderThread;
    private long lastTap = 0, touchStart = 0;
    private float initX, initY, initTouchX, initTouchY;
    private boolean hasMoved = false;
    private int animRow = 0, animSpeed = 250;
    private boolean animLoop = true, animActive = false;
    
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
        
        surfaceView = new SurfaceView(this);
        surfaceView.setZOrderOnTop(true);
        surfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                running = true;
                renderThread = new Thread(new RenderLoop());
                renderThread.start();
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                running = false;
                try { if (renderThread != null) renderThread.join(500); } catch (Exception e) {}
            }
        });
        
        surfaceView.setOnTouchListener((v, event) -> {
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
                    windowManager.updateViewLayout(surfaceView, layoutParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    long dt = System.currentTimeMillis() - touchStart;
                    if (hasMoved) return true;
                    if (dt < 300) {
                        long since = System.currentTimeMillis() - lastTap;
                        if (since < 400) { triggerAnim(6, 80, false); showToast("喵喵喵~"); }
                        else if (since < 800) { triggerAnim(4, 100, false); showToast("嗚！"); }
                        else { triggerAnim(2, 120, false); showToast("喵~"); }
                        lastTap = System.currentTimeMillis();
                    } else if (dt > 600) {
                        triggerAnim(8, 100, false);
                        showToast("呼噜噜...");
                    }
                    return true;
            }
            return false;
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
        windowManager.addView(surfaceView, layoutParams);
    }
    
    class RenderLoop implements Runnable {
        public void run() {
            int col = 0;
            long lastFrameTime = 0;
            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastFrameTime >= animSpeed) {
                    if (animActive) {
                        if (col >= cols) {
                            if (animLoop) { col = 0; }
                            else { animActive = false; triggerIdle(); }
                        }
                    }
                    if (animActive && col < cols) {
                        curFrameIdx = animRow * cols + col;
                        col++;
                    }
                    drawFrame();
                    lastFrameTime = now;
                }
                try { Thread.sleep(16); } catch (Exception e) {}
            }
        }
    }
    
    private void drawFrame() {
        SurfaceHolder holder = surfaceView.getHolder();
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null && curFrameIdx >= 0 && curFrameIdx < frames.size()) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                Bitmap frame = frames.get(curFrameIdx);
                if (frame != null && !frame.isRecycled()) {
                    Rect dst = new Rect(0, 0, surfaceView.getWidth(), surfaceView.getHeight());
                    canvas.drawBitmap(frame, null, dst, null);
                }
            }
        } finally {
            if (canvas != null) holder.unlockCanvasAndPost(canvas);
        }
    }
    
    private synchronized void triggerAnim(int row, int speed, boolean loop) {
        animRow = row;
        animSpeed = speed;
        animLoop = loop;
        animActive = true;
    }
    
    private synchronized void triggerIdle() {
        animRow = 0;
        animSpeed = 250;
        animLoop = true;
        animActive = true;
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
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, channelId)
            : new Notification.Builder(this);
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
        running = false;
        try { if (renderThread != null) renderThread.join(500); } catch (Exception e) {}
        if (windowManager != null && surfaceView != null) windowManager.removeView(surfaceView);
        for (Bitmap b : frames) { if (b != null && !b.isRecycled()) b.recycle(); }
        frames.clear();
        super.onDestroy();
    }
}
