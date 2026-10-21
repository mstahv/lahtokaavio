package fi.virit.lahtokaavio.plan;

import fi.virit.lahtokaavio.domain.Course;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the course of a class, leniently for open classes. An open class
 * ("10 Avoin") often has no course of its own in the course file but runs the
 * course of the matching age class, so when there is no exact match the word
 * "avoin" is dropped and the rest is looked up: first as such ("10"), then as
 * the age class of any gender ("D10", "H10"). The fallback is used only when
 * all such classes share one course.
 */
public final class CourseMatcher {

    private static final Pattern OPEN_WORD =
            Pattern.compile("(?iu)\\bavoin\\b");

    private CourseMatcher() {
    }

    /** True for open classes, e.g. "10 Avoin" or "Avoin rata". */
    public static boolean isOpenClass(String className) {
        return OPEN_WORD.matcher(className).find();
    }

    public static Optional<Course> find(Map<String, Course> coursesByClass, String className) {
        Course exact = coursesByClass.get(className);
        if (exact != null || !isOpenClass(className)) {
            return Optional.ofNullable(exact);
        }
        String base = OPEN_WORD.matcher(className).replaceAll(" ").trim().replaceAll("\\s+", " ");
        if (base.isEmpty()) {
            return Optional.empty();
        }
        Course byBase = coursesByClass.get(base);
        if (byBase != null) {
            return Optional.of(byBase);
        }
        Pattern ageClass = Pattern.compile("(?iu)\\p{L}+" + Pattern.quote(base.toLowerCase(Locale.ROOT)));
        List<Course> candidates = coursesByClass.entrySet().stream()
                .filter(e -> ageClass.matcher(e.getKey().toLowerCase(Locale.ROOT)).matches())
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
