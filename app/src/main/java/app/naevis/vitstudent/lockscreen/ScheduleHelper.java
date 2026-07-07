package app.naevis.vitstudent.lockscreen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class ScheduleHelper {

    public static final String PREFS_NAME = "schedule_prefs";
    public static final String KEY_ENABLED = "schedule_enabled";
    public static final String KEY_START_H = "schedule_start_h";
    public static final String KEY_START_M = "schedule_start_m";
    public static final String KEY_END_H   = "schedule_end_h";
    public static final String KEY_END_M   = "schedule_end_m";

    public static final int DEFAULT_START_H = 8;
    public static final int DEFAULT_START_M = 0;
    public static final int DEFAULT_END_H   = 18;
    public static final int DEFAULT_END_M   = 0;

    public static final String ACTION_SCHEDULE_START = "app.naevis.vitstudent.ACTION_SCHEDULE_START";
    public static final String ACTION_SCHEDULE_STOP  = "app.naevis.vitstudent.ACTION_SCHEDULE_STOP";

    private static final String ALARM_RECEIVER_CLASS = "app.naevis.vitstudent.receivers.ScheduleAlarmReceiver";

    public static final int PI_START_ID = 1001;
    public static final int PI_STOP_ID  = 1002;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isScheduleEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static int getStartHour(Context context) {
        return prefs(context).getInt(KEY_START_H, DEFAULT_START_H);
    }

    public static int getStartMinute(Context context) {
        return prefs(context).getInt(KEY_START_M, DEFAULT_START_M);
    }

    public static int getEndHour(Context context) {
        return prefs(context).getInt(KEY_END_H, DEFAULT_END_H);
    }

    public static int getEndMinute(Context context) {
        return prefs(context).getInt(KEY_END_M, DEFAULT_END_M);
    }

    public static void saveSchedule(Context context, boolean enabled, int startH, int startM, int endH, int endM) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_START_H, startH)
                .putInt(KEY_START_M, startM)
                .putInt(KEY_END_H, endH)
                .putInt(KEY_END_M, endM)
                .apply();
    }

    public static boolean isWithinScheduleWindow(Context context) {
        if (!isScheduleEnabled(context)) return true;

        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        int startMinutes = getStartHour(context) * 60 + getStartMinute(context);
        int endMinutes   = getEndHour(context) * 60 + getEndMinute(context);

        if (startMinutes <= endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        } else {
            return nowMinutes >= startMinutes || nowMinutes < endMinutes;
        }
    }

    public static void rescheduleAlarms(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        cancelAlarms(context, am);

        if (!isScheduleEnabled(context)) return;

        scheduleRepeatingAlarm(context, am, getStartHour(context), getStartMinute(context), PI_START_ID, ACTION_SCHEDULE_START);
        scheduleRepeatingAlarm(context, am, getEndHour(context), getEndMinute(context), PI_STOP_ID, ACTION_SCHEDULE_STOP);
    }

    public static void cancelAlarms(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        cancelAlarms(context, am);
    }

    private static void cancelAlarms(Context context, AlarmManager am) {
        am.cancel(buildPendingIntent(context, PI_START_ID, ACTION_SCHEDULE_START));
        am.cancel(buildPendingIntent(context, PI_STOP_ID, ACTION_SCHEDULE_STOP));
    }

    private static void scheduleRepeatingAlarm(Context context, AlarmManager am, int hour, int minute, int requestCode, String action) {
        PendingIntent pi = buildPendingIntent(context, requestCode, action);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long intervalDay = AlarmManager.INTERVAL_DAY;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), intervalDay, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), intervalDay, pi);
        }
    }

    public static PendingIntent buildPendingIntent(Context context, int requestCode, String action) {
        Intent intent = new Intent(action);
        intent.setClassName(context.getPackageName(), ALARM_RECEIVER_CLASS);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
