package app.naevis.vitstudent.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.naevis.vitstudent.R;
import app.naevis.vitstudent.activities.CourseNotesActivity;

/**
 * Adapter for the Notes tab course list in the Planner screen.
 * Each item shows a course card; tapping opens CourseNotesActivity.
 */
public class NotesCoursesAdapter extends RecyclerView.Adapter<NotesCoursesAdapter.ViewHolder> {

    public static class CourseInfo {
        public final String code;
        public final String title;
        public final boolean hasNotes;

        public CourseInfo(String code, String title, boolean hasNotes) {
            this.code = code;
            this.title = title;
            this.hasNotes = hasNotes;
        }
    }

    private List<CourseInfo> courses = new ArrayList<>();
    private final Context context;

    public NotesCoursesAdapter(Context context) {
        this.context = context;
    }

    public void setCourses(List<CourseInfo> courses) {
        this.courses = courses;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note_course, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CourseInfo course = courses.get(position);
        holder.tvTitle.setText(course.title != null ? course.title : course.code);
        holder.tvCode.setText(course.code);
        holder.viewIndicator.setVisibility(course.hasNotes ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, CourseNotesActivity.class);
            intent.putExtra("course_code", course.code);
            intent.putExtra("course_title", course.title);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCode;
        View viewIndicator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_note_course_title);
            tvCode = itemView.findViewById(R.id.tv_note_course_code);
            viewIndicator = itemView.findViewById(R.id.view_notes_indicator);
        }
    }
}
