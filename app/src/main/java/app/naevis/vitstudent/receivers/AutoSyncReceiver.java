package app.naevis.vitstudent.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import app.naevis.vitstudent.helpers.SettingsRepository;
import app.naevis.vitstudent.services.VTOPService;

public class AutoSyncReceiver extends BroadcastReceiver {

    public static final String ACTION_TRIGGER_AUTO_SYNC = "app.naevis.vitstudent.ACTION_TRIGGER_AUTO_SYNC";
    private static final int REQUEST_CODE = 999;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_TRIGGER_AUTO_SYNC.equals(intent.getAction())) {
            // Start VTOPService in auto-sync mode
            Intent serviceIntent = new Intent(context, VTOPService.class);
            serviceIntent.putExtra("is_auto_sync", true);
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Schedule the next alarm
            scheduleNextAutoSync(context);
        }
    }

    public static void scheduleNextAutoSync(Context context) {
        android.content.SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context);
        boolean enabled = sharedPreferences.getBoolean("auto_sync_enabled", false);
        if (!enabled) {
            cancelAutoSync(context);
            return;
        }

        int intervalHours = sharedPreferences.getInt("auto_sync_interval_hours", 2);
        long intervalMillis = intervalHours * 60 * 60 * 1000L;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AutoSyncReceiver.class);
        intent.setAction(ACTION_TRIGGER_AUTO_SYNC);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        long triggerAtMillis = System.currentTimeMillis() + intervalMillis;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    public static void cancelAutoSync(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AutoSyncReceiver.class);
        intent.setAction(ACTION_TRIGGER_AUTO_SYNC);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
}
