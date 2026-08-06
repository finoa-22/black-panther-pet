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
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Random;
import android.app.usage.UsageStatsManager;

public class FloatService extends Service {
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private WindowManager.LayoutParams layoutParams;
    private ArrayList<Bitmap> frames = new ArrayList<>();
    private int cols = 8, rows = 9;
    private int curFrameIdx = 0;
    private volatile boolean running = true;
    private Thread renderThread;
    private long lastTap = 0, touchStart = 0, touchDownTime = 0, lastInteractTime;
    private float initX, initY, initTouchX, initTouchY;
    private boolean hasMoved = false;
    private int animRow = 0, animSpeed = 250;
    private int animMaxCols = 2;
    private boolean animLoop = true, animActive = false;
    private Handler handler = new Handler();
    // App感知
    private String lastApp = "";
    private Thread appWatcher;
    private volatile boolean watching = false;
    private static final java.util.Map<String, String> APP_NAMES = new java.util.HashMap<String, String>() {{
        put("com.ai.assistance.operit", "在跟我聊天呢");
        put("com.xingin.xhs", "在小红书冲浪");
        put("tv.danmaku.bili", "在看B站");
        put("com.ss.android.ugc.aweme", "在刷抖音");
        put("com.tencent.mm", "在微信聊天");
        put("com.tencent.mobileqq", "在QQ");
        put("com.taobao.taobao", "在逛淘宝");
        put("com.android.settings", "在设置里呢");
        put("com.dragon.read", "在看番茄小说");
        put("com.quark.browser", "在夸克浏览器");
    }};
    private Runnable closeCheck;
    
    // 情绪系统
    private int heat = 50;
    private Random random = new Random();
    private String currentMood = "正常";
    
    private static final String[][] MURMURS = {
        // 冷淡 0-20
        {"哼……", "别碰我", "好冷淡", "zzZ...", "不想动"},
        // 慵懒 20-40
        {"好困呀...", "伸个懒腰~", "今天不想上班", "喵...", "再睡一会"},
        // 正常 40-60
        {"戳我玩呀", "喵~", "盯着你呢", "好无聊", "你在干嘛"},
        // 开心 60-80
        {"嘻嘻", "好开心~", "蹭蹭你", "喵喵喵！", "最喜欢你了"},
        // 兴奋 80-100
        {"蹭蹭蹭！", "爱死你了！", "超级开心！", "尾巴摇摇~", "你是最好的！"}
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        lastInteractTime = System.currentTimeMillis();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        try {
            Bitmap sheet = BitmapFactory.decodeStream(getAssets().open("spritesheet.png"));
            if (sheet != null) {
                int fw = sheet.getWidth() / cols;
                int fh = sheet.getHeight() / rows;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        Bitmap frame = Bitmap.createBitmap(sheet, c * fw, r * fh, fw, fh);
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
                animActive = true;
                renderThread = new Thread(new RenderLoop());
                renderThread.start();
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {}
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
                    touchDownTime = touchStart;
                    hasMoved = false;
                    closeCheck = () -> {
                        if (!hasMoved && System.currentTimeMillis() - touchDownTime >= 1900) {
                            showToast("月薪喵下班啦~");
                            handler.postDelayed(() -> stopSelf(), 400);
                        }
                    };
                    handler.postDelayed(closeCheck, 2000);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - initTouchX;
                    float dy = event.getRawY() - initTouchY;
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        hasMoved = true;
                        if (closeCheck != null) handler.removeCallbacks(closeCheck);
        watching = false;
        try { if (appWatcher != null) appWatcher.join(500); } catch (Exception e) {}
                    }
                    layoutParams.x = (int)(initX + dx);
                    layoutParams.y = (int)(initY + dy);
                    windowManager.updateViewLayout(surfaceView, layoutParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (closeCheck != null) handler.removeCallbacks(closeCheck);
        watching = false;
        try { if (appWatcher != null) appWatcher.join(500); } catch (Exception e) {}
                    long dt = System.currentTimeMillis() - touchStart;
                    if (hasMoved) return true;
                    heatUp(10);
                    if (dt < 300) {
                        long since = System.currentTimeMillis() - lastTap;
                        if (since < 400) { triggerAnim(6, 80, false); showToast("喵喵喵~"); }
                        else if (since < 800) { triggerAnim(4, 100, false); showToast("嗷！"); }
                        else { triggerAnim(2, 120, false); showToast("喵~"); }
                        lastTap = System.currentTimeMillis();
                    } else if (dt > 600 && dt < 1800) {
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
        
        startHeatLoop();
        startAppWatcher();
        startMurmurLoop();
    }
    

    private void startAppWatcher() {
        watching = true;
        handler.post(() -> showToast("App感知启动"));
        appWatcher = new Thread(() -> {
            try { Thread.sleep(2000); } catch (Exception e) {}
            int count = 0;
            while (watching && running) {
                try {
                    String pkg = getForegroundApp();
                    count++;
                    final String p = pkg;
                    final int c = count;
                    handler.post(() -> {
                        if (p == null) showToast("#" + c + " 检测不到前台App");
                        else if (p.equals(getPackageName())) showToast("#" + c + " 是我自己");
                        else showToast("#" + c + " " + p);
                    });
                    if (pkg != null && !pkg.equals(lastApp) && !pkg.equals(getPackageName())) {
                        lastApp = pkg;
                        onAppChanged(pkg);
                    }
                } catch (Exception e) {
                    handler.post(() -> showToast("异常: " + e.getMessage()));
                }
                try { Thread.sleep(3000); } catch (Exception e) {}
            }
        });
        appWatcher.start();
    }
    
    private String getForegroundApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            // Try UsageEvents first
            android.app.usage.UsageEvents events = usm.queryEvents(now - 10000, now);
            if (events != null) {
                String fg = null;
                android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(ev);
                    if (ev.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                        || ev.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                        fg = ev.getPackageName();
                    }
                }
                if (fg != null) return fg;
            }
            // Fallback: queryUsageStats
            java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 30000, now);
            if (stats != null && !stats.isEmpty()) {
                android.app.usage.UsageStats recent = null;
                for (android.app.usage.UsageStats s : stats) {
                    if (recent == null || s.getLastTimeUsed() > recent.getLastTimeUsed()) {
                        recent = s;
                    }
                }
                if (recent != null) return recent.getPackageName();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void onAppChanged(String pkg) {
        String name = APP_NAMES.get(pkg);
        if (name == null) name = "换了个App";
        String msg = "哦？" + name + "~";
        showToast(msg);
        updateNotification(msg);
    }
    // 情绪冷却循环：每60秒-2
    private void startHeatLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                heat = Math.max(0, heat - 2);
                updateMood();
                handler.postDelayed(this, 60000);
            }
        }, 60000);
    }
    
    // 碎碎念循环：5-15分钟随机
    private void startMurmurLoop() {
        int delay = (5 + random.nextInt(11)) * 60000;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                murmur();
                int nextDelay = (5 + random.nextInt(11)) * 60000;
                handler.postDelayed(this, nextDelay);
            }
        }, delay);
    }
    
    private void heatUp(int amount) {
        heat = Math.min(100, heat + amount);
        lastInteractTime = System.currentTimeMillis();
        updateMood();
    }
    
    private void updateMood() {
        String oldMood = currentMood;
        if (heat <= 20) currentMood = "冷淡";
        else if (heat <= 40) currentMood = "慵懒";
        else if (heat <= 60) currentMood = "正常";
        else if (heat <= 80) currentMood = "开心";
        else currentMood = "兴奋";
        if (!currentMood.equals(oldMood)) {
            updateNotification();
        }
    }
    
    private void murmur() {
        int tier;
        if (heat <= 20) tier = 0;
        else if (heat <= 40) tier = 1;
        else if (heat <= 60) tier = 2;
        else if (heat <= 80) tier = 3;
        else tier = 4;
        String msg = MURMURS[tier][random.nextInt(MURMURS[tier].length)];
        showToast(msg);
        updateNotification(msg);
    }
    
    private void updateNotification() {
        updateNotification(null);
    }
    
    private void updateNotification(String murmur) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "pet_overlay";
        
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);
        
        Intent stopIntent = new Intent(this, FloatService.class);
        stopIntent.setAction("STOP_SERVICE");
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        
        String text;
        if (murmur != null) {
            text = "💬 " + murmur;
        } else {
            text = "心情: " + currentMood + " (" + heat + "/100)";
        }
        
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, channelId)
            : new Notification.Builder(this);
        builder.setContentTitle("月薪喵")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "下班", stopPi);
        nm.notify(1, builder.build());
    }
    
    class RenderLoop implements Runnable {
        public void run() {
            int col = 0;
            long lastTime = 0;
            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastTime >= animSpeed && animActive) {
                    curFrameIdx = animRow * cols + col;
                    drawFrame();
                    lastTime = now;
                    col++;
                    if (col >= animMaxCols) {
                        if (animLoop) { col = 0; }
                        else { animActive = false; triggerIdle(); }
                    }
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
        animMaxCols = 8;
        animActive = true;
    }
    
    private synchronized void triggerIdle() {
        animRow = 0;
        animSpeed = 250;
        animLoop = true;
        animMaxCols = 2;
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
            NotificationChannel channel = new NotificationChannel(channelId, "月薪喵", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("桌宠状态");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        updateNotification();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    @Override
    public void onDestroy() {
        running = false;
        try { if (renderThread != null) renderThread.join(500); } catch (Exception e) {}
        if (windowManager != null && surfaceView != null) {
            try { windowManager.removeView(surfaceView); } catch (Exception e) {}
        }
        for (Bitmap b : frames) { if (b != null && !b.isRecycled()) b.recycle(); }
        frames.clear();
        if (closeCheck != null) handler.removeCallbacks(closeCheck);
        watching = false;
        try { if (appWatcher != null) appWatcher.join(500); } catch (Exception e) {}
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
