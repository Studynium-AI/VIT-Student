package app.naevis.vitstudent.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.helpers.SettingsRepository;
import app.naevis.vitstudent.interfaces.ExamsDao;
import app.naevis.vitstudent.interfaces.TimetableDao;
import app.naevis.vitstudent.models.Exam;
import app.naevis.vitstudent.models.Timetable;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            return;
        }

        if (!SettingsRepository.isSignedIn(context)) {
            return;
        }
        
        android.content.SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context);
        if (sharedPreferences.getBoolean("enableLockScreen", false)) {
            app.naevis.vitstudent.lockscreen.ScheduleHelper.rescheduleAlarms(context);
            if (app.naevis.vitstudent.lockscreen.ScheduleHelper.isWithinScheduleWindow(context)) {
                Intent serviceIntent = new Intent(context, app.naevis.vitstudent.lockscreen.LockScreenService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }

        SettingsRepository.clearNotificationPendingIntents(context);

        // Reschedule Auto-Sync if enabled
        try {
            app.naevis.vitstudent.receivers.AutoSyncReceiver.scheduleNextAutoSync(context);
        } catch (Exception ignored) {
        }

        AppDatabase appDatabase = AppDatabase.getInstance(context);
        TimetableDao timetableDao = appDatabase.timetableDao();
        ExamsDao examsDao = appDatabase.examsDao();

        timetableDao
                .getTimetable()
                .subscribeOn(Schedulers.single())
                .subscribe(new SingleObserver<List<Timetable>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Timetable> timetable) {
                        for (int i = 0; i < timetable.size(); ++i) {
                            try {
                                SettingsRepository.setTimetableNotifications(context, timetable.get(i));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });

        examsDao
                .getExams()
                .subscribeOn(Schedulers.single())
                .subscribe(new SingleObserver<List<Exam>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Exam> exams) {
                        SettingsRepository.setExamNotifications(context, exams);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });
    }
}
