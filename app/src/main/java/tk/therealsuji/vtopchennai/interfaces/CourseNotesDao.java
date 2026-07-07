package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import tk.therealsuji.vtopchennai.models.CourseNote;

@Dao
public interface CourseNotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(CourseNote note);

    @Query("SELECT * FROM course_notes WHERE course_code = :courseCode")
    Maybe<CourseNote> getNoteByCourse(String courseCode);
}
