package app.naevis.vitstudent.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "course_notes")
public class CourseNote {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "course_code")
    public String courseCode = "";

    @ColumnInfo(name = "content")
    public String content;
}
