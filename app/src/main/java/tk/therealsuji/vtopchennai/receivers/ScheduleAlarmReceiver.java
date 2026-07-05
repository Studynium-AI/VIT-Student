package tk.therealsuji.vtopchennai.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.lockscreen.LockScreenService;
import tk.therealsuji.vtopchennai.lockscreen.ScheduleHelper;

public class ScheduleAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences servicePrefs = SettingsRepository.getSharedPreferences(context);
        boolean serviceEnabled = servicePrefs.getBoolean("enableLockScreen", false);

        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(ScheduleHelper.ACTION_SCHEDULE_START)) {
            if (serviceEnabled && ScheduleHelper.isScheduleEnabled(context)) {
                Intent serviceIntent = new Intent(context, LockScreenService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
            rescheduleNextDay(
                    context,
                    ScheduleHelper.PI_START_ID,
                    ScheduleHelper.ACTION_SCHEDULE_START,
                    ScheduleHelper.getStartHour(context),
                    ScheduleHelper.getStartMinute(context)
            );
        } else if (action.equals(ScheduleHelper.ACTION_SCHEDULE_STOP)) {
            Intent serviceIntent = new Intent(context, LockScreenService.class);
            context.stopService(serviceIntent);
            rescheduleNextDay(
                    context,
                    ScheduleHelper.PI_STOP_ID,
                    ScheduleHelper.ACTION_SCHEDULE_STOP,
                    ScheduleHelper.getEndHour(context),
                    ScheduleHelper.getEndMinute(context)
            );
        }
    }

    private void rescheduleNextDay(Context context, int requestCode, String action, int hour, int minute) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        boolean canExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canExact = am.canScheduleExactAlarms();
        }
        if (!canExact) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);

        PendingIntent pi = ScheduleHelper.buildPendingIntent(context, requestCode, action);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
