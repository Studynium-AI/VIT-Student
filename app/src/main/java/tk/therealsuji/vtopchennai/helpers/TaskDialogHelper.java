package tk.therealsuji.vtopchennai.helpers;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.Task;

public class TaskDialogHelper {

    public static void showAddTaskDialog(Context context, String courseCode, String courseTitle, Runnable onSaved) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_add_task);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etTitle = dialog.findViewById(R.id.et_task_title);
        EditText etDesc = dialog.findViewById(R.id.et_task_desc);
        TextView tvStartTime = dialog.findViewById(R.id.tv_start_time);
        TextView tvEndTime = dialog.findViewById(R.id.tv_end_time);
        CheckBox cbDeadline = dialog.findViewById(R.id.cb_is_deadline);
        Button btnSave = dialog.findViewById(R.id.btn_save_task);

        Calendar startCal = Calendar.getInstance();
        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.HOUR_OF_DAY, 1);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        tvStartTime.setText(timeFormat.format(startCal.getTime()));
        tvEndTime.setText(timeFormat.format(endCal.getTime()));

        tvStartTime.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, hourOfDay, minute) -> {
                startCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                startCal.set(Calendar.MINUTE, minute);
                tvStartTime.setText(timeFormat.format(startCal.getTime()));
            }, startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE), false).show();
        });

        tvEndTime.setOnClickListener(v -> {
            new TimePickerDialog(context, (view, hourOfDay, minute) -> {
                endCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                endCal.set(Calendar.MINUTE, minute);
                tvEndTime.setText(timeFormat.format(endCal.getTime()));
            }, endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE), false).show();
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString();
            String desc = etDesc.getText().toString();

            if (title.isEmpty()) {
                Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            Task task = new Task();
            task.courseCode = courseCode;
            task.title = title;
            task.description = desc;
            task.startTime = startCal.getTimeInMillis();
            task.endTime = endCal.getTimeInMillis();
            task.isDeadline = cbDeadline.isChecked();
            task.isCompleted = false;

            AppDatabase.getInstance(context.getApplicationContext()).tasksDao().insert(task)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((taskId) -> {
                        scheduleTaskAlarm(context, taskId.intValue(), title, courseCode, startCal.getTimeInMillis());
                        Toast.makeText(context, "Task Added", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        if (onSaved != null) {
                            onSaved.run();
                        }
                    }, error -> {
                        Toast.makeText(context, "Error adding task", Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }

    private static void scheduleTaskAlarm(Context context, int taskId, String title, String courseCode, long timeInMillis) {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, tk.therealsuji.vtopchennai.receivers.TaskNotificationReceiver.class);
        intent.setAction(tk.therealsuji.vtopchennai.receivers.TaskNotificationReceiver.ACTION_TASK_REMINDER);
        intent.putExtra("taskId", taskId);
        intent.putExtra("title", title);
        intent.putExtra("course", courseCode);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context, taskId, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        } else {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
    }

    public static void loadTasksForBottomSheet(View bottomSheetLayout, String courseCode, AppDatabase appDatabase) {
        Context context = bottomSheetLayout.getContext();
        View divider = bottomSheetLayout.findViewById(R.id.divider_tasks);
        View tasksHeader = bottomSheetLayout.findViewById(R.id.layout_tasks_header);
        TextView tasksCount = bottomSheetLayout.findViewById(R.id.text_view_tasks_count);
        LinearLayout tasksContainer = bottomSheetLayout.findViewById(R.id.linear_layout_tasks_container);

        appDatabase.tasksDao().getTasksByCourse(courseCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<Task>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<Task> allTasks) {
                        List<Task> activeTasks = new ArrayList<>();
                        for (Task t : allTasks) {
                            if (!t.isCompleted) {
                                activeTasks.add(t);
                            }
                        }

                        if (activeTasks.isEmpty()) {
                            divider.setVisibility(View.GONE);
                            tasksHeader.setVisibility(View.GONE);
                            tasksContainer.setVisibility(View.GONE);
                            tasksContainer.removeAllViews();
                            return;
                        }

                        divider.setVisibility(View.VISIBLE);
                        tasksHeader.setVisibility(View.VISIBLE);
                        tasksContainer.setVisibility(View.VISIBLE);
                        tasksContainer.removeAllViews();

                        tasksCount.setText(String.valueOf(activeTasks.size()));

                        LayoutInflater inflater = LayoutInflater.from(context);
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault());

                        for (int i = 0; i < activeTasks.size(); i++) {
                            Task task = activeTasks.get(i);
                            View taskItemView = inflater.inflate(R.layout.item_course_task_small, tasksContainer, false);

                            TextView tvTitle = taskItemView.findViewById(R.id.tv_task_title);
                            TextView tvTime = taskItemView.findViewById(R.id.tv_task_time);
                            TextView tvDesc = taskItemView.findViewById(R.id.tv_task_desc);
                            ImageView ivChevron = taskItemView.findViewById(R.id.iv_task_chevron);
                            ImageView ivComplete = taskItemView.findViewById(R.id.iv_complete_task);

                            tvTitle.setText(task.title);

                            if (task.isDeadline) {
                                tvTime.setText("Deadline: " + sdf.format(new Date(task.endTime)));
                                tvTime.setTextColor(context.getResources().getColor(R.color.colorRed));
                            } else {
                                tvTime.setText(sdf.format(new Date(task.startTime)) + " - " + sdf.format(new Date(task.endTime)));
                                tvTime.setTextColor(MaterialColors.getColor(tvTime, R.attr.colorOnSurfaceVariant));
                            }

                            if (task.description != null && !task.description.trim().isEmpty()) {
                                tvDesc.setText(task.description);
                                ivChevron.setVisibility(View.VISIBLE);

                                View headerLayout = taskItemView.findViewById(R.id.layout_task_header);
                                headerLayout.setOnClickListener(v -> {
                                    if (tvDesc.getVisibility() == View.VISIBLE) {
                                        tvDesc.setVisibility(View.GONE);
                                        ivChevron.setRotation(0);
                                    } else {
                                        tvDesc.setVisibility(View.VISIBLE);
                                        ivChevron.setRotation(90);
                                    }
                                });
                            } else {
                                ivChevron.setVisibility(View.GONE);
                            }

                            ivComplete.setOnClickListener(v -> {
                                appDatabase.tasksDao().delete(task)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(() -> {
                                            loadTasksForBottomSheet(bottomSheetLayout, courseCode, appDatabase);
                                        });
                            });

                            tasksContainer.addView(taskItemView);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        e.printStackTrace();
                    }
                });
    }
}
