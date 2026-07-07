package tk.therealsuji.vtopchennai.lockscreen;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import tk.therealsuji.vtopchennai.activities.CourseNotesActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.Task;
import tk.therealsuji.vtopchennai.models.Timetable;

public class LockScreenClassesAdapter extends RecyclerView.Adapter<LockScreenClassesAdapter.ViewHolder> {

    private List<Timetable.AllData> classes = new ArrayList<>();
    private int expandedPosition = -1;
    private Context context;
    private final OnTaskAddListener onTaskAddListener;
    private final java.util.Map<String, List<Task>> tasksCache = new java.util.HashMap<>();

    public interface OnTaskAddListener {
        void onAddTaskClicked(String courseCode, String courseTitle);
    }

    public LockScreenClassesAdapter(Context context, OnTaskAddListener listener) {
        this.context = context;
        this.onTaskAddListener = listener;
    }

    private int firstUpcomingIndex = -1;

    public void clearTasksCache() {
        tasksCache.clear();
    }

    public void setClasses(List<Timetable.AllData> classes) {
        this.classes = classes;
        tasksCache.clear();
        updateFirstUpcomingIndex();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lock_screen_class, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position == 0) {
            updateFirstUpcomingIndex();
        }
        Timetable.AllData item = classes.get(position);

        holder.title.setText(item.courseTitle != null ? item.courseTitle : "Unknown Class");
        String formattedStart = item.startTime;
        String formattedEnd = item.endTime;
        try {
            java.text.SimpleDateFormat h24 = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH);
            java.text.SimpleDateFormat h12 = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH);
            if (item.startTime != null) {
                formattedStart = h12.format(h24.parse(item.startTime));
            }
            if (item.endTime != null) {
                formattedEnd = h12.format(h24.parse(item.endTime));
            }
        } catch (Exception ignored) {}
        holder.time.setText(formattedStart + " - " + formattedEnd);
        holder.code.setText(item.courseCode);
        holder.type.setText(item.courseType);

        if (item.attendancePercentage != null) {
            holder.attendance.setText(item.attendancePercentage + "%");
            if (item.attendancePercentage < 75) {
                holder.attendance.setTextColor(context.getResources().getColor(R.color.colorRed));
            } else {
                holder.attendance.setTextColor(context.getResources().getColor(R.color.colorGreen));
            }
        } else {
            holder.attendance.setText("--");
        }

        // Determine class status
        boolean isCompleted = false;
        boolean isOngoing = false;
        boolean isUpcoming = false;
        try {
            java.util.Calendar calNow = java.util.Calendar.getInstance();
            int nowMinutes = calNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calNow.get(java.util.Calendar.MINUTE);
            if (item.startTime != null && item.endTime != null) {
                String[] startParts = item.startTime.split(":");
                String[] endParts = item.endTime.split(":");
                int startMinutes = Integer.parseInt(startParts[0].trim()) * 60 + Integer.parseInt(startParts[1].trim());
                int endMinutes = Integer.parseInt(endParts[0].trim()) * 60 + Integer.parseInt(endParts[1].trim());

                if (nowMinutes >= endMinutes) {
                    isCompleted = true;
                } else if (nowMinutes >= startMinutes) {
                    isOngoing = true;
                } else {
                    isUpcoming = true;
                }
            }
        } catch (Exception ignored) {}

        // Adjust padding and card details for thinned/ongoing/upcoming classes
        int paddingVertical = (int) ((isCompleted ? 6 : (isOngoing ? 16 : 12)) * context.getResources().getDisplayMetrics().density);
        int paddingHorizontal = (int) (16 * context.getResources().getDisplayMetrics().density);
        holder.cardContent.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        // Adjust bottom margin programmatically to stack completed cards tighter
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        if (layoutParams != null) {
            int margin = (int) ((isCompleted ? 6 : 12) * context.getResources().getDisplayMetrics().density);
            layoutParams.bottomMargin = margin;
            holder.itemView.setLayoutParams(layoutParams);
        }

        // Apply card styling (background and border)
        com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) holder.itemView;
        if (isCompleted) {
            int bgColor = android.graphics.Color.parseColor("#0DFFFFFF"); // 5% white
            int strokeColor = android.graphics.Color.parseColor("#13FFFFFF"); // Faint white
            cardView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
            cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
            cardView.setStrokeWidth((int) (1 * context.getResources().getDisplayMetrics().density));
            
            holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            holder.title.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"));
            holder.time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            holder.time.setTextColor(android.graphics.Color.parseColor("#66FFFFFF"));
            
            holder.subDetails.setVisibility(View.GONE);
        } else if (isOngoing) {
            int bgColor = android.graphics.Color.parseColor("#26FFFFFF"); // 15% white
            int strokeColor = android.graphics.Color.parseColor("#00E676"); // Neon Green
            cardView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
            cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
            cardView.setStrokeWidth((int) (2 * context.getResources().getDisplayMetrics().density));
            
            holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            holder.title.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            holder.time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            holder.time.setTextColor(android.graphics.Color.parseColor("#E0E0E0"));
            
            holder.subDetails.setVisibility(View.VISIBLE);
        } else {
            int bgColor = android.graphics.Color.parseColor("#1AFFFFFF"); // 10% white
            int strokeColor = android.graphics.Color.parseColor("#26FFFFFF"); // Subtle white
            cardView.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
            cardView.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
            cardView.setStrokeWidth((int) (1 * context.getResources().getDisplayMetrics().density));
            
            holder.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            holder.title.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            holder.time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            holder.time.setTextColor(android.graphics.Color.parseColor("#99FFFFFF"));
            
            holder.subDetails.setVisibility(View.VISIBLE);
        }

        bindProgress(holder, item, position);

        boolean isExpanded = position == expandedPosition;
        holder.expandableLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        // Load tasks and badge
        holder.loadTasks(item.courseCode, isExpanded, isCompleted);

        // Dim completed classes unless they are expanded
        holder.itemView.setAlpha((isCompleted && !isExpanded) ? 0.5f : 1.0f);

        if (isExpanded) {
            // Load extra data (faculty, venue, skippable)
            holder.loadDetails(item.slotId);
        }

        holder.itemView.setOnClickListener(v -> {
            int previousExpanded = expandedPosition;
            if (isExpanded) {
                expandedPosition = -1;
                notifyItemChanged(previousExpanded);
            } else {
                expandedPosition = holder.getAdapterPosition();
                if (previousExpanded != -1) {
                    notifyItemChanged(previousExpanded);
                }
                notifyItemChanged(expandedPosition);
            }
        });

        holder.addTaskButton.setOnClickListener(v -> {
            if (onTaskAddListener != null) {
                onTaskAddListener.onAddTaskClicked(item.courseCode, item.courseTitle);
            }
        });

        holder.notesButton.setOnClickListener(v -> {
            Intent intent = new Intent(context, CourseNotesActivity.class);
            intent.putExtra("course_code", item.courseCode);
            intent.putExtra("course_title", item.courseTitle);
            intent.putExtra("from_lockscreen", true);
            context.startActivity(intent);
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.recycle();
    }

    @Override
    public int getItemCount() {
        return classes != null ? classes.size() : 0;
    }

    private void updateFirstUpcomingIndex() {
        firstUpcomingIndex = -1;
        if (classes == null) return;
        
        try {
            java.util.Calendar calNow = java.util.Calendar.getInstance();
            int nowMinutes = calNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calNow.get(java.util.Calendar.MINUTE);
            
            for (int i = 0; i < classes.size(); i++) {
                Timetable.AllData item = classes.get(i);
                if (item.startTime == null) continue;
                String[] startParts = item.startTime.split(":");
                int startMinutes = Integer.parseInt(startParts[0].trim()) * 60 + Integer.parseInt(startParts[1].trim());
                if (nowMinutes < startMinutes) {
                    firstUpcomingIndex = i;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private void bindProgress(ViewHolder holder, Timetable.AllData item, int position) {
        if (item.startTime == null || item.endTime == null) {
            holder.progressBar.setVisibility(View.GONE);
            holder.statusText.setVisibility(View.GONE);
            holder.progressSection.setVisibility(View.GONE);
            return;
        }

        try {
            java.util.Calendar calNow = java.util.Calendar.getInstance();
            int nowMinutes = calNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calNow.get(java.util.Calendar.MINUTE);

            String[] startParts = item.startTime.split(":");
            String[] endParts = item.endTime.split(":");
            int startMinutes = Integer.parseInt(startParts[0].trim()) * 60 + Integer.parseInt(startParts[1].trim());
            int endMinutes = Integer.parseInt(endParts[0].trim()) * 60 + Integer.parseInt(endParts[1].trim());

            if (nowMinutes < startMinutes) {
                // Upcoming class
                if (position == firstUpcomingIndex) {
                    holder.progressSection.setVisibility(View.VISIBLE);
                    holder.progressBar.setVisibility(View.VISIBLE);
                    holder.statusText.setVisibility(View.VISIBLE);
                    holder.statusText.setAlpha(1.0f);
                    int diff = startMinutes - nowMinutes;
                    holder.statusText.setText("Starts in " + formatMinutes(diff));
                    holder.progressBar.setProgress(0);
                    holder.statusText.setTextColor(context.getResources().getColor(R.color.colorYellow));
                } else {
                    holder.progressSection.setVisibility(View.GONE);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.statusText.setVisibility(View.GONE);
                }
            } else if (nowMinutes >= startMinutes && nowMinutes < endMinutes) {
                // Ongoing class
                holder.progressSection.setVisibility(View.VISIBLE);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.statusText.setVisibility(View.VISIBLE);
                holder.statusText.setAlpha(1.0f);
                int elapsed = nowMinutes - startMinutes;
                int duration = endMinutes - startMinutes;
                int remaining = endMinutes - nowMinutes;
                int progress = (int) ((elapsed * 100.0) / duration);

                holder.statusText.setText("Ongoing • " + formatMinutes(remaining) + " left");
                holder.progressBar.setProgress(progress);
                holder.statusText.setTextColor(context.getResources().getColor(R.color.colorGreen));
            } else {
                // Completed class - hide entirely
                holder.progressSection.setVisibility(View.GONE);
                holder.progressBar.setVisibility(View.GONE);
                holder.statusText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            holder.progressSection.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
            holder.statusText.setVisibility(View.GONE);
        }
    }

    private String formatMinutes(int minutes) {
        if (minutes >= 60) {
            int h = minutes / 60;
            int m = minutes % 60;
            if (m == 0) {
                return h + "h";
            }
            return h + "h " + m + "m";
        }
        return minutes + "m";
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, time, code, type, attendance, facultyInfo, skippableClasses, statusText, taskBadge;
        LinearLayout expandableLayout, cardContent, subDetails, progressSection, tasksSection, tasksContainer;
        MaterialButton addTaskButton, notesButton;
        ProgressBar progressBar;
        Disposable disposable;
        Disposable tasksDisposable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_class_title);
            time = itemView.findViewById(R.id.tv_class_time);
            code = itemView.findViewById(R.id.tv_class_code);
            type = itemView.findViewById(R.id.tv_class_type);
            attendance = itemView.findViewById(R.id.tv_attendance);
            facultyInfo = itemView.findViewById(R.id.tv_faculty_info);
            skippableClasses = itemView.findViewById(R.id.tv_skippable_classes);
            expandableLayout = itemView.findViewById(R.id.layout_expandable_details);
            addTaskButton = itemView.findViewById(R.id.btn_add_task);
            notesButton = itemView.findViewById(R.id.btn_notes);
            progressBar = itemView.findViewById(R.id.pb_class_progress);
            statusText = itemView.findViewById(R.id.tv_class_status);

            // New Layout Views
            cardContent = itemView.findViewById(R.id.layout_class_card_content);
            subDetails = itemView.findViewById(R.id.layout_class_sub_details);
            progressSection = itemView.findViewById(R.id.layout_class_progress_section);
            taskBadge = itemView.findViewById(R.id.tv_task_badge);
            tasksSection = itemView.findViewById(R.id.layout_tasks_section);
            tasksContainer = itemView.findViewById(R.id.layout_tasks_container);
        }

        public void loadTasks(String courseCode, boolean isExpanded, boolean isCompleted) {
            if (tasksDisposable != null && !tasksDisposable.isDisposed()) {
                tasksDisposable.dispose();
            }

            if (tasksCache.containsKey(courseCode)) {
                List<Task> activeTasks = tasksCache.get(courseCode);
                updateTasksUi(activeTasks, isExpanded, isCompleted);
                return;
            }

            tasksDisposable = AppDatabase.getInstance(context).tasksDao().getTasksByCourse(courseCode)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(allTasks -> {
                        List<Task> activeTasks = new ArrayList<>();
                        for (Task t : allTasks) {
                            if (!t.isCompleted) {
                                activeTasks.add(t);
                            }
                        }
                        tasksCache.put(courseCode, activeTasks);
                        updateTasksUi(activeTasks, isExpanded, isCompleted);
                    }, throwable -> {
                        throwable.printStackTrace();
                    });
        }

        private void updateTasksUi(List<Task> activeTasks, boolean isExpanded, boolean isCompleted) {
            if (activeTasks.isEmpty()) {
                taskBadge.setVisibility(View.GONE);
                tasksSection.setVisibility(View.GONE);
                tasksContainer.removeAllViews();
            } else {
                taskBadge.setVisibility(View.VISIBLE);
                taskBadge.setText(activeTasks.size() + (activeTasks.size() == 1 ? " Task" : " Tasks"));
                if (isCompleted) {
                    taskBadge.setTextColor(android.graphics.Color.parseColor("#80FF9F0A"));
                } else {
                    taskBadge.setTextColor(android.graphics.Color.parseColor("#FF9F0A"));
                }
                
                if (isExpanded) {
                    tasksSection.setVisibility(View.VISIBLE);
                    tasksContainer.removeAllViews();
                    LayoutInflater inflater = LayoutInflater.from(context);
                    for (int i = 0; i < activeTasks.size(); i++) {
                        Task task = activeTasks.get(i);
                        View taskItemView = inflater.inflate(R.layout.item_lock_screen_task_small, tasksContainer, false);
                        
                        View layoutHeader = taskItemView.findViewById(R.id.layout_task_small_header);
                        TextView tvTitle = taskItemView.findViewById(R.id.tv_task_small_title);
                        TextView tvDesc = taskItemView.findViewById(R.id.tv_task_small_desc);
                        android.widget.ImageView ivChevron = taskItemView.findViewById(R.id.iv_task_small_chevron);
                        
                        tvTitle.setText((i + 1) + ". " + task.title);
                        if (task.description != null && !task.description.trim().isEmpty()) {
                            tvDesc.setText(task.description);
                            tvDesc.setVisibility(View.GONE);
                            ivChevron.setVisibility(View.VISIBLE);
                            ivChevron.setRotation(0);
                            
                            layoutHeader.setOnClickListener(v -> {
                                if (tvDesc.getVisibility() == View.VISIBLE) {
                                    tvDesc.setVisibility(View.GONE);
                                    ivChevron.setRotation(0);
                                } else {
                                    tvDesc.setVisibility(View.VISIBLE);
                                    ivChevron.setRotation(90);
                                }
                            });
                        } else {
                            tvDesc.setVisibility(View.GONE);
                            ivChevron.setVisibility(View.GONE);
                        }
                        tasksContainer.addView(taskItemView);
                    }
                } else {
                    tasksSection.setVisibility(View.GONE);
                    tasksContainer.removeAllViews();
                }
            }
        }

        public void loadDetails(int slotId) {
            CoursesDao coursesDao = AppDatabase.getInstance(context).coursesDao();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }

            facultyInfo.setText("Loading details...");
            skippableClasses.setText("");

            disposable = coursesDao.getCourse(slotId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(courseData -> {
                        String fac = courseData.faculty != null ? courseData.faculty : "Unknown";
                        String ven = courseData.venue != null ? courseData.venue : "Unknown";
                        facultyInfo.setText("Teacher: " + fac + "\nVenue: " + ven);

                        if (courseData.attendanceAttended != null && courseData.attendanceTotal != null) {
                            int total = courseData.attendanceTotal;
                            int attended = courseData.attendanceAttended;
                            
                            // To maintain 75% attendance: (attended) / (total + skips) >= 0.75
                            // skips <= (attended / 0.75) - total
                            int maxSkips = (int) (attended / 0.75) - total;
                            
                            if (maxSkips > 0) {
                                skippableClasses.setText("You can skip " + maxSkips + " more classes.");
                                skippableClasses.setTextColor(context.getResources().getColor(R.color.colorGreen));
                            } else if (maxSkips == 0) {
                                skippableClasses.setText("You cannot skip any more classes.");
                                skippableClasses.setTextColor(context.getResources().getColor(R.color.colorYellow));
                            } else {
                                skippableClasses.setText("You are short of attendance!");
                                skippableClasses.setTextColor(context.getResources().getColor(R.color.colorRed));
                            }
                        }
                    }, throwable -> {
                        facultyInfo.setText("Failed to load details.");
                    });
        }

        public void recycle() {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
            if (tasksDisposable != null && !tasksDisposable.isDisposed()) {
                tasksDisposable.dispose();
            }
        }
    }
}
