package app.naevis.vitstudent.helpers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import app.naevis.vitstudent.interfaces.AssignmentsDao;
import app.naevis.vitstudent.interfaces.AttendanceDao;
import app.naevis.vitstudent.interfaces.CoursesDao;
import app.naevis.vitstudent.interfaces.ExamsDao;
import app.naevis.vitstudent.interfaces.MarksDao;
import app.naevis.vitstudent.interfaces.ReceiptsDao;
import app.naevis.vitstudent.interfaces.SpotlightDao;
import app.naevis.vitstudent.interfaces.StaffDao;
import app.naevis.vitstudent.interfaces.TasksDao;
import app.naevis.vitstudent.interfaces.TimetableDao;
import app.naevis.vitstudent.models.Assignment;
import app.naevis.vitstudent.models.Attachment;
import app.naevis.vitstudent.models.Attendance;
import app.naevis.vitstudent.models.Course;
import app.naevis.vitstudent.models.CumulativeMark;
import app.naevis.vitstudent.models.Exam;
import app.naevis.vitstudent.models.Mark;
import app.naevis.vitstudent.models.Receipt;
import app.naevis.vitstudent.models.Slot;
import app.naevis.vitstudent.models.Spotlight;
import app.naevis.vitstudent.models.Staff;
import app.naevis.vitstudent.models.Task;
import app.naevis.vitstudent.models.Timetable;
import app.naevis.vitstudent.models.CourseNote;
import app.naevis.vitstudent.interfaces.CourseNotesDao;

@Database(
        entities = {
                Assignment.class,
                Attachment.class,
                Attendance.class,
                Course.class,
                CumulativeMark.class,
                Exam.class,
                Mark.class,
                Receipt.class,
                Slot.class,
                Spotlight.class,
                Staff.class,
                Task.class,
                Timetable.class,
                CourseNote.class
        },
        version = 5,
        autoMigrations = {
                @AutoMigration(from = 1, to = 2),
        }
)
public abstract class AppDatabase extends RoomDatabase {
    public static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "vit_student")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public static synchronized void deleteDatabase(Context context) {
        if (instance != null) {
            instance.close();
        }

        instance = null;
        context.deleteDatabase("vit_student");
        context.deleteDatabase("vtop"); // Delete the deprecated database (used till < v4.0)
    }

    public abstract AssignmentsDao assignmentsDao();

    public abstract AttendanceDao attendanceDao();

    public abstract CoursesDao coursesDao();

    public abstract ExamsDao examsDao();

    public abstract MarksDao marksDao();

    public abstract ReceiptsDao receiptsDao();

    public abstract SpotlightDao spotlightDao();

    public abstract StaffDao staffDao();

    public abstract TasksDao tasksDao();

    public abstract TimetableDao timetableDao();

    public abstract CourseNotesDao courseNotesDao();

    // Manual Migrations
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE receipts RENAME TO receipts_old");
            database.execSQL("CREATE TABLE receipts (number INTEGER NOT NULL PRIMARY KEY, amount REAL, date INTEGER)");
            database.execSQL("INSERT INTO receipts (number, amount) SELECT number, amount FROM receipts_old");
            database.execSQL("UPDATE receipts SET date = 0");
            database.execSQL("DROP TABLE receipts_old");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `course_code` TEXT, `title` TEXT, `description` TEXT, `start_time` INTEGER NOT NULL, `end_time` INTEGER NOT NULL, `is_deadline` INTEGER NOT NULL, `is_completed` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `course_notes` (`course_code` TEXT NOT NULL, `content` TEXT, PRIMARY KEY(`course_code`))");
        }
    };
}
