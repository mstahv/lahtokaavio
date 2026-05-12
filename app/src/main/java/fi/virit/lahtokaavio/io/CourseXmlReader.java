package fi.virit.lahtokaavio.io;

import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.StartPlace;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads an IOF XML CourseData file (v2 format) and returns a map of
 * class name → {@link Course}. Two course-name conventions are supported:
 * <ul>
 *   <li>L1-style: each {@code <Course>} carries a {@code <ClassShortName>}
 *       element with whitespace-separated class names.</li>
 *   <li>L2/RRTR-style: there is no {@code <ClassShortName>} and the class
 *       names are embedded directly in {@code <CourseName>}.</li>
 * </ul>
 *
 * IOF v3 is not yet supported.
 */
@org.springframework.stereotype.Component
public final class CourseXmlReader {

    public Map<String, Course> read(InputStream input) {
        Document document = parse(input);
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
            CourseDescriptor descriptor = parseCourse(courseEl);
            for (String className : descriptor.classNames) {
                coursesByClass.put(className, descriptor.course);
            }
        }
        return coursesByClass;
    }

    private CourseDescriptor parseCourse(Element courseEl) {
        String courseName = textOfChild(courseEl, "CourseName");
        if (courseName == null) {
            throw new IllegalArgumentException("Course element missing <CourseName>");
        }

        String classShortNameText = textOfChild(courseEl, "ClassShortName");
        List<String> classNames = splitClassNames(
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
        List<String> controlCodes = readControlCodes(variation);

        Course course = new Course(courseName, new StartPlace(startPointCode), controlCodes);
        return new CourseDescriptor(course, classNames);
    }

    private List<String> readControlCodes(Element variation) {
        List<String> codes = new ArrayList<>();
        NodeList controls = variation.getElementsByTagName("CourseControl");
        for (int i = 0; i < controls.getLength(); i++) {
            Element control = (Element) controls.item(i);
            if (control.getParentNode() != variation) {
                continue;
            }
            String code = textOfChild(control, "ControlCode");
            if (code != null && !code.isBlank()) {
                codes.add(code);
            }
        }
        return codes;
    }

    private List<String> splitClassNames(String raw) {
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
