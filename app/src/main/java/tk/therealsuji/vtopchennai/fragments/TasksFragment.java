package tk.therealsuji.vtopchennai.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.TasksAdapter;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;

public class TasksFragment extends Fragment {

    private RecyclerView rvTasks;
    private TextView emptyState;
    private TasksAdapter adapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    public TasksFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tasks, container, false);

        rvTasks = view.findViewById(R.id.rv_tasks);
        emptyState = view.findViewById(R.id.tv_empty_tasks);
        View appBarLayout = view.findViewById(R.id.app_bar);

        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TasksAdapter(requireContext(), task -> {
            // Delete or Complete Task
            task.isCompleted = true; // For now marking as complete removes it or updates it
            disposables.add(
                AppDatabase.getInstance(requireContext()).tasksDao().delete(task)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> loadTasks())
            );
        });
        rvTasks.setAdapter(adapter);

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

            rvTasks.setPaddingRelative(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)
            );
        });

        loadTasks();

        return view;
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
