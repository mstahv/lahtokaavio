package fi.virit.lahtokaavio.plan;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.StartPlace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseMatcherTest {

    private final Course course10 = new Course("D10, H10", new StartPlace("STA1"), List.of(31));
    private final Course course14 = new Course("D14, H14", new StartPlace("STA1"), List.of(32));
    private final Course course12d = new Course("D12", new StartPlace("STA1"), List.of(33));
    private final Course course12h = new Course("H12", new StartPlace("STA1"), List.of(34));

    private final Map<String, Course> courses = Map.of(
            "D10", course10, "H10", course10,
            "D14", course14, "H14", course14, "14 Avoin", course12d,
            "D12", course12d, "H12", course12h);

    @Test
    void exactMatchWins() {
        assertThat(CourseMatcher.find(courses, "14 Avoin")).contains(course12d);
        assertThat(CourseMatcher.find(courses, "H14")).contains(course14);
    }

    @Test
    void openClassFallsBackToItsAgeClass() {
        assertThat(CourseMatcher.find(courses, "10 Avoin")).contains(course10);
        assertThat(CourseMatcher.find(courses, "10 avoin")).contains(course10);
        assertThat(CourseMatcher.find(courses, "Avoin 10")).contains(course10);
    }

    @Test
    void openClassFallsBackToAClassNamedLikeTheRest() {
        Course course = new Course("10", new StartPlace("STA2"), List.of(40));
        assertThat(CourseMatcher.find(Map.of("10", course, "D10", course10), "10 Avoin")).contains(course);
    }

    @Test
    void noGuessWhenTheAgeClassesRunDifferentCourses() {
        assertThat(CourseMatcher.find(courses, "12 Avoin")).isEmpty();
    }

    @Test
    void onlyOpenClassesAreMatchedLeniently() {
        assertThat(CourseMatcher.find(courses, "10")).isEmpty();
        assertThat(CourseMatcher.find(courses, "D16")).isEmpty();
        assertThat(CourseMatcher.find(courses, "Avoin")).isEmpty();
    }

    @Test
    void recognisesOpenClasses() {
        assertThat(CourseMatcher.isOpenClass("10 Avoin")).isTrue();
        assertThat(CourseMatcher.isOpenClass("AVOIN rata")).isTrue();
        assertThat(CourseMatcher.isOpenClass("H21")).isFalse();
        assertThat(CourseMatcher.isOpenClass("Avoinna")).isFalse();
    }
}
