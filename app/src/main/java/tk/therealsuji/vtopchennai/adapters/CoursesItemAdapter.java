package tk.therealsuji.vtopchennai.adapters;

import android.content.Context;
import android.text.Html;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.TaskDialogHelper;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.models.Course;

/**
 * ┬─── Courses Hierarchy
 * ├─ {@link tk.therealsuji.vtopchennai.fragments.ViewPagerFragment}
 * ├─ {@link CoursesAdapter}        - ViewPager2
 * ╰→ {@link CoursesItemAdapter}    - RecyclerView (Current File)
 */
public class CoursesItemAdapter extends RecyclerView.Adapter<CoursesItemAdapter.ViewHolder> {
    static final int VIEW_TITLE = 1;
    static final int VIEW_DEFAULT = 2;

    List<Course.AllData> course;
    Set<String> courseTypes;

    public CoursesItemAdapter(List<Course.AllData> course) {
        Map<String, Course.AllData> courseMap = new HashMap<>();
        this.courseTypes = new ArraySet<>();

        for (int i = 0; i < course.size(); ++i) {
            Course.AllData courseItem = course.get(i);

            courseTypes.add(courseItem.courseType);

            if (!courseMap.containsKey(courseItem.courseType)) {
                courseItem.slots = new ArrayList<>();
                courseMap.put(courseItem.courseType, courseItem);
            }

            Objects.requireNonNull(courseMap.get(courseItem.courseType)).slots.add(courseItem.slot);
        }

        this.course = new ArrayList<>();

        this.course.add(courseMap.get("theory"));
        this.course.add(courseMap.get("lab"));
        this.course.add(courseMap.get("project"));

        this.course.removeAll(Collections.singleton(null));
    }

    @NonNull
    @Override
    public CoursesItemAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout courseItem;

        if (viewType == VIEW_TITLE) {

            courseItem = (LinearLayout) LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.layout_item_courses_title, parent, false);
        } else {
            courseItem = (LinearLayout) LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.layout_item_courses, parent, false);
        }

        return new ViewHolder(courseItem);
    }

    @Override
    public void onBindViewHolder(@NonNull CoursesItemAdapter.ViewHolder holder, int position) {
        if (position == 0) {
            holder.setCourseTitle(
                    this.course.get(0).courseTitle,
                    this.courseTypes.contains("theory"),
                    this.courseTypes.contains("lab"),
                    this.courseTypes.contains("project")
            );
        } else {
            holder.setCourseItem(this.course.get(position - 1));
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TITLE;
        }

        return VIEW_DEFAULT;
    }

    @Override
    public int getItemCount() {
        return this.course.size() + 1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout courseItem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            this.courseItem = (LinearLayout) itemView;
        }

        public void setCourseTitle(String courseTitle, boolean theory, boolean lab, boolean project) {
            TextView title = this.courseItem.findViewById(R.id.text_view_course_title);
            ChipGroup courseTypes = this.courseItem.findViewById(R.id.chip_group_course_types);

            title.setText(courseTitle);
            courseTypes.removeAllViews();

            if (theory) {
                Chip chip = new Chip(this.courseItem.getContext());
                chip.setChipIconResource(R.drawable.ic_theory);
                chip.setText(R.string.theory);

                courseTypes.addView(chip);
            }

            if (lab) {
                Chip chip = new Chip(this.courseItem.getContext());
                chip.setChipIconResource(R.drawable.ic_lab);
                chip.setText(R.string.lab);

                courseTypes.addView(chip);
            }

            if (project) {
                Chip chip = new Chip(this.courseItem.getContext());
                chip.setChipIconResource(R.drawable.ic_project);
                chip.setText(R.string.project);

                courseTypes.addView(chip);
            }
        }

        public void setCourseItem(Course.AllData courseItem) {
            TextView faculty = this.courseItem.findViewById(R.id.text_view_faculty);
            TextView venue = this.courseItem.findViewById(R.id.text_view_venue);
            TextView attendanceText = this.courseItem.findViewById(R.id.text_view_attendance);
            ChipGroup slots = this.courseItem.findViewById(R.id.chip_group_slots);
            ProgressBar attendanceProgress = this.courseItem.findViewById(R.id.progress_bar_attendance);

            faculty.setText(Html.fromHtml(this.courseItem.getContext().getString(R.string.faculty, courseItem.faculty), Html.FROM_HTML_MODE_LEGACY));
            venue.setText(Html.fromHtml(this.courseItem.getContext().getString(R.string.venue, courseItem.venue), Html.FROM_HTML_MODE_LEGACY));

            int chipIconResource = R.drawable.ic_theory;

            if (courseItem.courseType.equals("lab")) {
                chipIconResource = R.drawable.ic_lab;
            } else if (courseItem.courseType.equals("project")) {
                chipIconResource = R.drawable.ic_project;
            }

            slots.removeAllViews();

            for (int i = 0; i < courseItem.slots.size(); ++i) {
                Chip slot = new Chip(this.courseItem.getContext());
                slot.setChipIconResource(chipIconResource);
                slot.setText(courseItem.slots.get(i));

                slots.addView(slot);
            }

            if (courseItem.attendancePercentage == null) {
                attendanceText.setText(this.courseItem.getContext().getString(R.string.na));
                attendanceProgress.setProgress(0);
                return;
            }

            attendanceText.setText(new DecimalFormat("#'%'").format(courseItem.attendancePercentage));
            attendanceProgress.setProgress(courseItem.attendancePercentage);

            if (SettingsRepository.getCGPA(this.courseItem.getContext()) < 9 && courseItem.attendancePercentage < 75) {
                attendanceProgress.setSecondaryProgress(75);
            }

            attendanceText.setOnClickListener(view -> {
                TextView attendanceText1 = (TextView) view;

                if (attendanceText1.getText().toString().contains("%")) {
                    attendanceText1.setText(String.format(Locale.ENGLISH, "%d/%d", courseItem.attendanceAttended, courseItem.attendanceTotal));
                } else {
                    attendanceText1.setText(String.format(Locale.ENGLISH, "%d%%", courseItem.attendancePercentage));
                }
            });

            this.courseItem.setOnClickListener(view -> {
                Context context = view.getContext();
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
                View bottomSheetLayout = View.inflate(context, R.layout.layout_bottom_sheet_course_info, null);
                bottomSheetDialog.setContentView(bottomSheetLayout);
                bottomSheetDialog.show();

                TextView courseTitle = bottomSheetLayout.findViewById(R.id.text_view_course_title);
                TextView courseCodeView = bottomSheetLayout.findViewById(R.id.text_view_course_code);
                TextView facultyView = bottomSheetLayout.findViewById(R.id.text_view_faculty);
                TextView venueView = bottomSheetLayout.findViewById(R.id.text_view_venue);
                TextView attendanceExcessText = bottomSheetLayout.findViewById(R.id.text_view_attendance_excess);
                TextView attendanceTextBS = bottomSheetLayout.findViewById(R.id.text_view_attendance);
                Chip slotChip = bottomSheetLayout.findViewById(R.id.chip_slot);
                ProgressBar attendanceProgressBS = bottomSheetLayout.findViewById(R.id.progress_bar_attendance);

                courseTitle.setText(courseItem.courseTitle);
                courseCodeView.setText(courseItem.courseCode);
                facultyView.setText(Html.fromHtml(context.getString(R.string.faculty, courseItem.faculty), Html.FROM_HTML_MODE_LEGACY));
                venueView.setText(Html.fromHtml(context.getString(R.string.venue, courseItem.venue), Html.FROM_HTML_MODE_LEGACY));

                if (courseItem.courseType.equals("lab")) {
                    slotChip.setChipIconResource(R.drawable.ic_lab);
                } else {
                    slotChip.setChipIconResource(R.drawable.ic_theory);
                }

                StringBuilder sbSlots = new StringBuilder();
                if (courseItem.slots != null) {
                    for (int i = 0; i < courseItem.slots.size(); ++i) {
                        if (i > 0) sbSlots.append(", ");
                        sbSlots.append(courseItem.slots.get(i));
                    }
                } else {
                    sbSlots.append(courseItem.slot);
                }
                slotChip.setText(sbSlots.toString());

                if (courseItem.attendancePercentage == null) {
                    attendanceTextBS.setText(context.getString(R.string.na));
                    attendanceProgressBS.setProgress(0);
                } else {
                    attendanceTextBS.setText(new DecimalFormat("#'%'").format(courseItem.attendancePercentage));
                    attendanceProgressBS.setProgress(courseItem.attendancePercentage);

                    if (SettingsRepository.getCGPA(context) < 9) {
                        double attendanceExcess = 100 * courseItem.attendanceAttended - 75 * courseItem.attendanceTotal;

                        if (courseItem.attendancePercentage < 75) {
                            attendanceExcess = Math.floor(attendanceExcess / 25);
                            attendanceProgressBS.setSecondaryProgress(75);
                        } else {
                            attendanceExcess = Math.floor(attendanceExcess / 75);
                        }

                        attendanceExcessText.setVisibility(View.VISIBLE);
                        attendanceExcessText.setText(new DecimalFormat("+#;-#").format(attendanceExcess));

                        if (attendanceExcess < 0) {
                            attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorError));
                        } else if (attendanceExcess == 0) {
                            attendanceExcessText.setTextColor(MaterialColors.getColor(attendanceExcessText, R.attr.colorSecondary));
                        }
                    }
                }

                bottomSheetLayout.findViewById(R.id.progress_bar_loading).setVisibility(View.GONE);
                bottomSheetLayout.findViewById(R.id.linear_layout_container).setVisibility(View.VISIBLE);

                AppDatabase appDatabase = AppDatabase.getInstance(context.getApplicationContext());
                TaskDialogHelper.loadTasksForBottomSheet(bottomSheetLayout, courseItem.courseCode, appDatabase);

                View btnAddTask = bottomSheetLayout.findViewById(R.id.btn_add_task);
                btnAddTask.setOnClickListener(v -> {
                    TaskDialogHelper.showAddTaskDialog(context, courseItem.courseCode, courseItem.courseTitle, () -> {
                        TaskDialogHelper.loadTasksForBottomSheet(bottomSheetLayout, courseItem.courseCode, appDatabase);
                    });
                });
            });
        }
    }
}
