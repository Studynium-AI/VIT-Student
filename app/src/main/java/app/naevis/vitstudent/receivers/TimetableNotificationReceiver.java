package app.naevis.vitstudent.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.helpers.NotificationHelper;
import app.naevis.vitstudent.helpers.SettingsRepository;
import app.naevis.vitstudent.interfaces.ExamsDao;
import app.naevis.vitstudent.interfaces.TimetableDao;
import app.naevis.vitstudent.models.Timetable;

public class TimetableNotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Calendar calendar = Calendar.getInstance();
        Calendar calendarFuture = Calendar.getInstance();

        Calendar calendarFirstHourToday = Calendar.getInstance();
        Calendar calendarLastHourToday = Calendar.getInstance();

        calendarFuture.add(Calendar.MINUTE, 30);
        calendarFirstHourToday.set(Calendar.HOUR_OF_DAY, 0);
        calendarFirstHourToday.set(Calendar.MINUTE, 0);
        calendarLastHourToday.set(Calendar.HOUR_OF_DAY, 23);
        calendarLastHourToday.set(Calendar.MINUTE, 59);

        SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

        int day = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        String currentTime = hour24.format(calendar.getTime());
        String futureTime = hour24.format(calendarFuture.getTime());

        AppDatabase appDatabase = AppDatabase.getInstance(context);
        ExamsDao examsDao = appDatabase.examsDao();
        TimetableDao timetableDao = appDatabase.timetableDao();

        examsDao
                .isExamsOngoing(calendarFirstHourToday.getTimeInMillis(), calendarLastHourToday.getTimeInMillis())
                .subscribeOn(Schedulers.single())
                .subscribe(new SingleObserver<Boolean>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull Boolean isOngoing) {
                        if (isOngoing) {
                            return;
                        }

                        timetableDao
                                .getUpcoming(day, currentTime, futureTime)
                                .subscribeOn(Schedulers.single())
                                .subscribe(new SingleObserver<Timetable.AllData>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onSuccess(Timetable.@NonNull AllData timetableItem) {
                                        try {
                                            if ("lab".equalsIgnoreCase(timetableItem.courseType)) {
                                                timetableDao.getOngoing(day, currentTime)
                                                        .subscribeOn(Schedulers.single())
                                                        .subscribe(new SingleObserver<Timetable.AllData>() {
                                                            @Override
                                                            public void onSubscribe(@NonNull Disposable d) {}

                                                            @Override
                                                            public void onSuccess(Timetable.@NonNull AllData ongoingItem) {
                                                                if ("lab".equalsIgnoreCase(ongoingItem.courseType)
                                                                        && ongoingItem.courseCode != null
                                                                        && ongoingItem.courseCode.equalsIgnoreCase(timetableItem.courseCode)) {
                                                                    // Do not notify: same lab is already ongoing
                                                                } else {
                                                                    sendNotification(context, timetableItem);
                                                                }
                                                            }

                                                            @Override
                                                            public void onError(@NonNull Throwable e) {
                                                                sendNotification(context, timetableItem);
                                                            }
                                                        });
                                            } else {
                                                sendNotification(context, timetableItem);
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        timetableDao
                                                .getOngoing(day, currentTime)
                                                .subscribeOn(Schedulers.single())
                                                .subscribe(new SingleObserver<Timetable.AllData>() {
                                                    @Override
                                                    public void onSubscribe(@NonNull Disposable d) {
                                                    }

                                                    @Override
                                                    public void onSuccess(Timetable.@NonNull AllData ongoingItem) {
                                                        try {
                                                            NotificationHelper notificationHelper = new NotificationHelper(context);
                                                            notificationHelper.getManager().notify(
                                                                    SettingsRepository.NOTIFICATION_ID_TIMETABLE,
                                                                    notificationHelper.notifyOngoing(ongoingItem).build()
                                                            );
                                                        } catch (Exception ignored) {
                                                        }
                                                    }

                                                    @Override
                                                    public void onError(@NonNull Throwable e) {
                                                    }
                                                });
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }
                });
    }

    private void sendNotification(Context context, Timetable.AllData timetableItem) {
        try {
            NotificationHelper notificationHelper = new NotificationHelper(context);
            notificationHelper.getManager().notify(
                    SettingsRepository.NOTIFICATION_ID_TIMETABLE,
                    notificationHelper.notifyUpcoming(timetableItem).build()
            );
        } catch (Exception ignored) {
        }
    }
}
