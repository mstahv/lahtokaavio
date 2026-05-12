package fi.virit.lahtokaavio.io;

import static org.assertj.core.api.Assertions.assertThat;

import fi.virit.lahtokaavio.domain.Course;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseXmlReaderTest {

    private final CourseXmlReader reader = new CourseXmlReader();

    @Test
    void readsClassShortNameConvention() throws Exception {
        Map<String, Course> coursesByClass = readResource("/Viking-Line-L1.xml");

        assertThat(coursesByClass).containsKey("H15");
        Course h15 = coursesByClass.get("H15");
        assertThat(h15.name()).isEqualTo("1");
        assertThat(h15.startPlace().code()).isEqualTo("STA1");
        assertThat(h15.controlCodes()).startsWith("172", "170", "164").contains("100");

        // Course shared by multiple classes is registered for each name
        assertThat(coursesByClass).containsKey("H21A").containsKey("H35");
        assertThat(coursesByClass.get("H21A")).isSameAs(coursesByClass.get("H35"));
    }

    @Test
    void readsClassNameEmbeddedInCourseNameConvention() throws Exception {
        Map<String, Course> coursesByClass = readResource("/Viking-Line-L2.xml");

        assertThat(coursesByClass).containsKey("D10").containsKey("H10");
        Course d10 = coursesByClass.get("D10");
        assertThat(d10.startPlace().code()).isEqualTo("STA2");
        assertThat(d10.controlCodes()).isNotEmpty();
        // Both D10 and H10 share the same course definition
        assertThat(coursesByClass.get("D10")).isSameAs(coursesByClass.get("H10"));
    }

    @Test
    void readsIofV3CourseDataViaIofdomain() throws Exception {
        Map<String, Course> coursesByClass = readResource("/iof3-courses.xml");

        // Purple-Pen-style v3 export with no ClassCourseAssignment block.
        // The course Name doubles as the class name.
        assertThat(coursesByClass).containsKey("D10RR");
        Course d10rr = coursesByClass.get("D10RR");
        assertThat(d10rr.startPlace().code()).isEqualTo("STA2");
        // First control of the course (Start / Finish entries skipped).
        assertThat(d10rr.controlCodes()).startsWith("113", "166", "133");
        assertThat(d10rr.controlCodes()).doesNotContain("STA2", "FIN1");

        // Numeric-named courses ("2A", "3A", …) survive the same path.
        assertThat(coursesByClass).containsKeys("2A", "3A", "4A", "4B");
    }

    @Test
    void differentStartPlacesAreDistinct() throws Exception {
        Map<String, Course> coursesByClass = readResource("/Viking-Line-L1.xml");
        // "Kunto" courses use STA2 in L1
        boolean sta1Seen = coursesByClass.values().stream()
                .anyMatch(c -> "STA1".equals(c.startPlace().code()));
        boolean sta2Seen = coursesByClass.values().stream()
                .anyMatch(c -> "STA2".equals(c.startPlace().code()));
        assertThat(sta1Seen).isTrue();
        assertThat(sta2Seen).isTrue();
    }

    private Map<String, Course> readResource(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            return reader.read(in);
        }
    }
}
