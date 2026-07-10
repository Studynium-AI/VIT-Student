package app.naevis.vitstudent.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.adapters.NotesCoursesAdapter;
import app.naevis.vitstudent.adapters.TasksAdapter;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.models.CourseBasicInfo;

public class TasksFragment extends Fragment {

    private RecyclerView rvTasks;
    private RecyclerView rvNotesCourses;
    private TextView emptyState;
    private TextView emptyNotes;
    private TasksAdapter adapter;
    private NotesCoursesAdapter notesAdapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    private MaterialButtonToggleGroup toggleGroup;

    public TasksFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tasks, container, false);

        rvTasks = view.findViewById(R.id.rv_tasks);
        rvNotesCourses = view.findViewById(R.id.rv_notes_courses);
        emptyState = view.findViewById(R.id.tv_empty_tasks);
        emptyNotes = view.findViewById(R.id.tv_empty_notes);
        toggleGroup = view.findViewById(R.id.toggle_group_tabs);
        View appBarLayout = view.findViewById(R.id.app_bar);

        // Tasks RecyclerView
        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter(requireContext(), task -> {
            task.isCompleted = true;
            app.naevis.vitstudent.helpers.TaskDialogHelper.cancelTaskAlarms(requireContext(), task.id, task.isDeadline);
            disposables.add(
                AppDatabase.getInstance(requireContext()).tasksDao().delete(task)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> loadTasks())
            );
        });
        rvTasks.setAdapter(adapter);

        // Notes RecyclerView
        rvNotesCourses.setLayoutManager(new LinearLayoutManager(requireContext()));
        notesAdapter = new NotesCoursesAdapter(requireContext());
        rvNotesCourses.setAdapter(notesAdapter);

        // Tab toggle listener
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_tab_tasks) {
                showTasksTab();
            } else if (checkedId == R.id.btn_tab_notes) {
                showNotesTab();
            }
        });
        // Default: Tasks selected
        toggleGroup.check(R.id.btn_tab_tasks);

        getParentFragmentManager().setFragmentResultListener("customInsets", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");
            float pixelDensity = getResources().getDisplayMetrics().density;

            appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            int bottomPadding = (int) (bottomNavigationHeight + 20 * pixelDensity);
            rvTasks.setPaddingRelative(systemWindowInsetLeft, 0, systemWindowInsetRight, bottomPadding);
            rvNotesCourses.setPaddingRelative(systemWindowInsetLeft, 0, systemWindowInsetRight, bottomPadding);
        });

        loadTasks();

        return view;
    }

    private void showTasksTab() {
        rvTasks.setVisibility(View.VISIBLE);
        rvNotesCourses.setVisibility(View.GONE);
        emptyNotes.setVisibility(View.GONE);
        // emptyState visibility is managed by loadTasks()
    }

    private void showNotesTab() {
        rvTasks.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        rvNotesCourses.setVisibility(View.VISIBLE);
        loadNotesCourses();
    }

    private void loadTasks() {
        disposables.add(
            AppDatabase.getInstance(requireContext()).tasksDao().getAllTasks()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tasks -> {
                    if (tasks.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        rvTasks.setVisibility(View.GONE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                        rvTasks.setVisibility(View.VISIBLE);
                        adapter.setTasks(tasks);
                    }
                }, error -> {
                    error.printStackTrace();
                })
        );
    }

    private void loadNotesCourses() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        disposables.add(
            db.coursesDao().getCoursesWithTitles()
                .subscribeOn(Schedulers.io())
                .zipWith(
                    db.courseNotesDao().getAllNoteCourseCodes().subscribeOn(Schedulers.io()),
                    (courses, notesCodes) -> {
                        Set<String> notesSet = new HashSet<>(notesCodes);
                        List<NotesCoursesAdapter.CourseInfo> result = new ArrayList<>();
                        for (CourseBasicInfo c : courses) {
                            boolean hasNotes = notesSet.contains(c.code);
                            result.add(new NotesCoursesAdapter.CourseInfo(c.code, c.title, hasNotes));
                        }
                        return result;
                    }
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(courseInfos -> {
                    if (courseInfos.isEmpty()) {
                        emptyNotes.setVisibility(View.VISIBLE);
                        rvNotesCourses.setVisibility(View.GONE);
                    } else {
                        emptyNotes.setVisibility(View.GONE);
                        rvNotesCourses.setVisibility(View.VISIBLE);
                        notesAdapter.setCourses(courseInfos);
                    }
                }, error -> {
                    emptyNotes.setVisibility(View.VISIBLE);
                    rvNotesCourses.setVisibility(View.GONE);
                    error.printStackTrace();
                })
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
