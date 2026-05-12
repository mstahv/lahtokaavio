package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.StartPlace;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.orienteering.datastandard._3.ClassCourseAssignment;
import org.orienteering.datastandard._3.ControlType;
import org.orienteering.datastandard._3.CourseControl;
import org.orienteering.datastandard._3.CourseData;
import org.orienteering.datastandard._3.RaceCourseData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads an IOF XML CourseData file and returns a map of class name →
 * {@link Course}. Both <b>v2</b> (legacy XML with {@code <IOFVersion>} child
 * element) and <b>v3</b> (root attribute {@code iofVersion="3.0"} +
 * namespace {@code http://www.orienteering.org/datastandard/3.0}) are
 * supported.
 *
 * <p>Within v2, two course-name conventions are tolerated:
 * <ul>
 *   <li>L1-style: each {@code <Course>} carries a {@code <ClassShortName>}
 *       element with whitespace-separated class names.</li>
 *   <li>L2/RRTR-style: there is no {@code <ClassShortName>} and the class
 *       names are embedded directly in {@code <CourseName>}.</li>
 * </ul>
 *
 * <p>For v3 we delegate to the {@code in.virit:iofdomain} JAXB bindings.
 * If the file carries a {@code <ClassCourseAssignment>} block it's used as
 * the authoritative class → course map; otherwise the course's own
 * {@code <Name>} doubles as the class name (matching v2's L2-style).
 */
@org.springframework.stereotype.Component
public final class CourseXmlReader {

    public Map<String, Course> read(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            String version = peekIofVersion(bytes);
            if (version != null && version.startsWith("3")) {
                return readV3(bytes);
            }
            return readV2(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read course XML", e);
        }
    }

    /**
     * Inspects the root element to figure out which IOF spec version the
     * file follows. v3 uses a root attribute {@code iofVersion="3.0"}; v2
     * tucks the version into a child element {@code <IOFVersion version=...>}.
     */
    private String peekIofVersion(byte[] bytes) {
        Document doc = parse(new ByteArrayInputStream(bytes));
        Element root = doc.getDocumentElement();
        String attr = root.getAttribute("iofVersion");
        if (!attr.isEmpty()) {
            return attr;
        }
        NodeList list = root.getElementsByTagName("IOFVersion");
        if (list.getLength() > 0) {
            String v = ((Element) list.item(0)).getAttribute("version");
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    // --- IOF v2 ----------------------------------------------------------

    private Map<String, Course> readV2(byte[] bytes) {
        Document document = parse(new ByteArrayInputStream(bytes));
        Element root = document.getDocumentElement();
        if (!"CourseData".equals(root.getLocalName()) && !"CourseData".equals(root.getNodeName())) {
            throw new IllegalArgumentException(
                    "Expected <CourseData> root element, got: " + root.getNodeName());
        }

        Map<String, Course> coursesByClass = new LinkedHashMap<>();
        NodeList courseNodes = root.getElementsByTagName("Course");
        for (int i = 0; i < courseNodes.getLength(); i++) {
            Element courseEl = (Element) courseNodes.item(i);
            if (courseEl.getParentNode() != root) {
                continue;
            }
            CourseDescriptor descriptor = parseV2Course(courseEl);
            for (String className : descriptor.classNames) {
                coursesByClass.put(className, descriptor.course);
            }
        }
        return coursesByClass;
    }

    private CourseDescriptor parseV2Course(Element courseEl) {
        String courseName = textOfChild(courseEl, "CourseName");
        if (courseName == null) {
            throw new IllegalArgumentException("Course element missing <CourseName>");
        }
        String classShortNameText = textOfChild(courseEl, "ClassShortName");
        List<String> classNames = splitTokens(
                classShortNameText != null ? classShortNameText : courseName);

        Element variation = firstChildElement(courseEl, "CourseVariation");
        if (variation == null) {
            throw new IllegalArgumentException(
                    "Course '" + courseName + "' has no <CourseVariation>");
        }
        String startPointCode = textOfChild(variation, "StartPointCode");
        if (startPointCode == null) {
            throw new IllegalArgumentException(
                    "Course '" + courseName + "' has no <StartPointCode>");
        }
        List<Integer> controlCodes = readV2ControlCodes(variation);
        Course course = new Course(courseName, new StartPlace(startPointCode), controlCodes);
        return new CourseDescriptor(course, classNames);
    }

    private List<Integer> readV2ControlCodes(Element variation) {
        List<Integer> codes = new ArrayList<>();
        NodeList controls = variation.getElementsByTagName("CourseControl");
        for (int i = 0; i < controls.getLength(); i++) {
            Element control = (Element) controls.item(i);
            if (control.getParentNode() != variation) {
                continue;
            }
            String code = textOfChild(control, "ControlCode");
            if (code != null && !code.isBlank()) {
                codes.add(parseControlCode(code));
            }
        }
        return codes;
    }

    private static int parseControlCode(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Non-numeric control code: '" + raw + "'", e);
        }
    }

    // --- IOF v3 ----------------------------------------------------------

    private Map<String, Course> readV3(byte[] bytes) {
        CourseData data = unmarshalV3(bytes);
        Map<String, Course> coursesByClass = new LinkedHashMap<>();
        for (RaceCourseData race : data.getRaceCourseData()) {
            // The optional ClassCourseAssignment block is the canonical
            // class → course mapping when present; build a course lookup
            // by name first so we can resolve those.
            Map<String, Course> coursesByName = new LinkedHashMap<>();
            for (org.orienteering.datastandard._3.Course rc : race.getCourse()) {
                coursesByName.put(rc.getName(), toCourse(rc));
            }
            // Apply explicit assignments first.
            for (ClassCourseAssignment a : race.getClassCourseAssignment()) {
                String className = a.getClassName();
                String courseName = a.getCourseName();
                Course course = coursesByName.get(courseName);
                if (className != null && course != null) {
                    coursesByClass.put(className, course);
                }
            }
            // If no assignments were given, fall back to treating each
            // course's Name as a (possibly space-separated) list of class
            // names — matches v2's L2/RRTR convention.
            if (race.getClassCourseAssignment().isEmpty()) {
                for (Map.Entry<String, Course> e : coursesByName.entrySet()) {
                    for (String className : splitTokens(e.getKey())) {
                        coursesByClass.put(className, e.getValue());
                    }
                }
            }
        }
        return coursesByClass;
    }

    private CourseData unmarshalV3(byte[] bytes) {
        try {
            JAXBContext ctx = JAXBContext.newInstance(CourseData.class);
            return (CourseData) ctx.createUnmarshaller()
                    .unmarshal(new ByteArrayInputStream(bytes));
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Failed to parse IOF v3 course XML: "
                    + e.getMessage(), e);
        }
    }

    private Course toCourse(org.orienteering.datastandard._3.Course raw) {
        String startCode = null;
        List<Integer> controlCodes = new ArrayList<>();
        for (CourseControl cc : raw.getCourseControl()) {
            ControlType type = cc.getType();
            String code = cc.getControl().isEmpty() ? null : cc.getControl().getFirst();
            if (type == ControlType.START) {
                startCode = code;
            } else if (type == ControlType.CONTROL && code != null) {
                controlCodes.add(parseControlCode(code));
            }
            // FINISH / CROSSING_POINT / END_OF_MARKED_ROUTE: not part of the
            // ordered control sequence we use for planning / CSV export.
        }
        if (startCode == null) {
            throw new IllegalArgumentException(
                    "Course '" + raw.getName() + "' has no Start CourseControl");
        }
        return new Course(raw.getName(), new StartPlace(startCode), controlCodes);
    }

    // --- Shared helpers --------------------------------------------------

    private List<String> splitTokens(String raw) {
        List<String> names = new ArrayList<>();
        for (String token : raw.trim().split("\\s+")) {
            if (!token.isBlank()) {
                names.add(token);
            }
        }
        return names;
    }

    private String textOfChild(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private Document parse(InputStream input) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Failed to parse course XML", e);
        }
    }

    private record CourseDescriptor(Course course, List<String> classNames) {
    }
}
