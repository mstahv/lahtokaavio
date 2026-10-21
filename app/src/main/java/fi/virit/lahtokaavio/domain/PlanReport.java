package fi.virit.lahtokaavio.domain;

import java.util.List;
import java.util.Map;

/**
 * What the user should know about a generated plan.
 *
 * @param missingCourses  classes left out of the plan, as no course was found
 * @param borrowedCourses classes planned with another class' course (an open
 *                        class using its age class' course): class → course name
 */
public record PlanReport(List<String> missingCourses, Map<String, String> borrowedCourses) {

    public PlanReport {
        missingCourses = List.copyOf(missingCourses);
        borrowedCourses = Map.copyOf(borrowedCourses);
    }
}
