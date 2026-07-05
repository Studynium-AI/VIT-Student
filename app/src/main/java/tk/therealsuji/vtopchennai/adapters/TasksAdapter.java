package tk.therealsuji.vtopchennai.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.Task;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.ViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private Context context;
    private final OnTaskActionListener actionListener;

    public interface OnTaskActionListener {
        void onCompleteTask(Task task);
    }

    public TasksAdapter(Context context, OnTaskActionListener listener) {
        this.context = context;
        this.actionListener = listener;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task task = tasks.get(position);

        holder.title.setText(task.title);
        holder.desc.setText(task.description != null ? task.description : "");
        holder.code.setText(task.courseCode != null ? task.courseCode : "");
        
        holder.code.setVisibility(task.courseCode != null && !task.courseCode.isEmpty() ? View.VISIBLE : View.GONE);
        holder.desc.setVisibility(task.description != null && !task.description.isEmpty() ? View.VISIBLE : View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault());
        String startStr = sdf.format(new Date(task.startTime));
        String endStr = sdf.format(new Date(task.endTime));

        if (task.isDeadline) {
            holder.time.setText("Deadline: " + endStr);
            holder.time.setTextColor(context.getResources().getColor(R.color.colorRed));
        } else {
            holder.time.setText(startStr + " - " + endStr);
            // reset color to default if needed (using theme colors in XML usually handles it)
        }

        holder.btnComplete.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCompleteTask(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, code, time;
        MaterialButton btnComplete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_task_title);
            desc = itemView.findViewById(R.id.tv_task_desc);
            code = itemView.findViewById(R.id.tv_task_code);
            time = itemView.findViewById(R.id.tv_task_time);
            btnComplete = itemView.findViewById(R.id.btn_complete_task);
        }
    }
}
