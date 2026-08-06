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
import android.graphics.PixelFormat;
import android.os.Build;
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
    private long lastTap = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        try {
            Bitmap sheet = BitmapFactory.decodeStream(getAssets().open("spritesheet.png"));
            if (sheet != null) {
                // Just show first frame
                int fw = sheet.getWidth() / 8;
                int fh = sheet.getHeight() / 9;
                Bitmap first = Bitmap.createBitmap(sheet, 0, 0, fw, fh);
                imageView.setImageBitmap(first);
                sheet.recycle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                long dt = System.currentTimeMillis() - lastTap;
                if (dt < 400) {
                    showToast("喵喵喵~");
                } else if (dt < 800) {
                    showToast("嗷！");
                } else {
                    showToast("喵~");
                }
                lastTap = System.currentTimeMillis();
            }
            return true;
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
        if (windowManager != null && imageView != null) windowManager.removeView(imageView);
        super.onDestroy();
    }
}
