package app.naevis.vitstudent.lockscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import app.naevis.vitstudent.R;
import app.naevis.vitstudent.activities.MainActivity;

public class LockScreenService extends Service {

    private static final String CHANNEL_ID = "lock_screen_service";
    private static final int NOTIFICATION_ID = 1001;

    private BroadcastReceiver lockScreenReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        lockScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    if (!ScheduleHelper.isWithinScheduleWindow(context)) return;

                    android.app.KeyguardManager km = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && (km.isKeyguardLocked() || Intent.ACTION_SCREEN_OFF.equals(action))) {
                        Intent lockIntent = new Intent(context, LockScreenActivity.class);
                        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        try {
                            context.startActivity(lockIntent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(lockScreenReceiver, filter);

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lock Screen Tasks Service")
                .setContentText("Listening for lock screen activations...")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (lockScreenReceiver != null) {
            unregisterReceiver(lockScreenReceiver);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lock Screen Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
