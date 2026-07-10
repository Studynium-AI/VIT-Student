package app.naevis.vitstudent.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import app.naevis.vitstudent.models.CourseNote;

@Dao
public interface CourseNotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(CourseNote note);

    @Query("SELECT * FROM course_notes WHERE course_code = :courseCode")
    Maybe<CourseNote> getNoteByCourse(String courseCode);

    @Query("SELECT course_code FROM course_notes WHERE content IS NOT NULL AND content != '' AND content != '[]'")
    io.reactivex.rxjava3.core.Single<java.util.List<String>> getAllNoteCourseCodes();
}
