package app.naevis.vitstudent.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.OnConflictStrategy;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import app.naevis.vitstudent.models.Task;

@Dao
public interface TasksDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insert(Task task);

    @Update
    Completable update(Task task);

    @Delete
    Completable delete(Task task);

    @Query("SELECT * FROM tasks ORDER BY end_time ASC")
    Single<List<Task>> getAllTasks();

    @Query("SELECT * FROM tasks WHERE is_completed = 0 ORDER BY end_time ASC")
    Single<List<Task>> getActiveTasks();

    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY end_time DESC")
    Single<List<Task>> getCompletedTasks();

    @Query("SELECT * FROM tasks WHERE course_code = :courseCode ORDER BY end_time ASC")
    Single<List<Task>> getTasksByCourse(String courseCode);

    @Query("UPDATE tasks SET is_completed = 1 WHERE id = :taskId")
    Completable completeTask(int taskId);

    @Query("DELETE FROM tasks WHERE id = :taskId")
    Completable deleteTaskById(int taskId);
}
