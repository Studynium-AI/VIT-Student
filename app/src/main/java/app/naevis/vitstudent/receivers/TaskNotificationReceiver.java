package app.naevis.vitstudent.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.activities.MainActivity;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.models.Task;

public class TaskNotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_TASK_REMINDER = "app.naevis.vitstudent.ACTION_TASK_REMINDER";
    public static final String ACTION_TASK_COMPLETE = "app.naevis.vitstudent.ACTION_TASK_COMPLETE";

    private static final String CHANNEL_ID = "task_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        int taskId = intent.getIntExtra("taskId", -1);
        if (taskId == -1) return;

        if (action.equals(ACTION_TASK_REMINDER)) {
            String title = intent.getStringExtra("title");
            String course = intent.getStringExtra("course");
            showNotification(context, taskId, title, course);
        } else if (action.equals(ACTION_TASK_COMPLETE)) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(taskId);

            AppDatabase.getInstance(context).tasksDao().getAllTasks()
                    .subscribeOn(Schedulers.io())
                    .subscribe(tasks -> {
                        for (Task t : tasks) {
                            if (t.id == taskId) {
                                t.isCompleted = true;
                                AppDatabase.getInstance(context).tasksDao().delete(t).blockingSubscribe();
                                break;
                            }
                        }
                    }, err -> {});
        }
    }

    private void showNotification(Context context, int taskId, String title, String course) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context, taskId, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent completeIntent = new Intent(context, TaskNotificationReceiver.class);
        completeIntent.setAction(ACTION_TASK_COMPLETE);
        completeIntent.putExtra("taskId", taskId);
        PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                context, taskId, completeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle(course != null && !course.isEmpty() ? course + " Task Reminder" : "Task Reminder")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(mainPendingIntent)
                .addAction(R.drawable.ic_done, "Mark Complete", completePendingIntent);

        manager.notify(taskId, builder.build());
    }
}
