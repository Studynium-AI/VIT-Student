package app.naevis.vitstudent.models;

/**
 * Lightweight projection used by {@link app.naevis.vitstudent.interfaces.CoursesDao#getCoursesWithTitles()}
 * to fetch only course code and title without requiring all Course columns.
 */
public class CourseBasicInfo {
    public String code;
    public String title;
}
