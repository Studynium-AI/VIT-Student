package tk.therealsuji.vtopchennai.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Timetable;

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
            tk.therealsuji.vtopchennai.lockscreen.ScheduleHelper.rescheduleAlarms(context);
            if (tk.therealsuji.vtopchennai.lockscreen.ScheduleHelper.isWithinScheduleWindow(context)) {
                Intent serviceIntent = new Intent(context, tk.therealsuji.vtopchennai.lockscreen.LockScreenService.class);
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
            tk.therealsuji.vtopchennai.receivers.AutoSyncReceiver.scheduleNextAutoSync(context);
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
