package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.server.VaadinSession;
import fi.virit.lahtokaavio.domain.CompetitionParameters;
import fi.virit.lahtokaavio.domain.Course;
import fi.virit.lahtokaavio.domain.Entry;
import fi.virit.lahtokaavio.domain.Registrations;
import fi.virit.lahtokaavio.domain.Series;
import fi.virit.lahtokaavio.domain.StartDraw;
import fi.virit.lahtokaavio.domain.StartPlan;
import fi.virit.lahtokaavio.domain.WorkspaceSnapshot;
import fi.virit.lahtokaavio.plan.StartPlanGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the user's competition planning state across navigations within one
 * Vaadin session. Keeping it here lets the entry view, plan view and the
 * registrations editor share the same data without ad-hoc session attributes.
 *
 * <p>Class-to-count is stored separately from the parsed {@link Registrations}
 * because users can edit a count after the CSV is loaded — those edits live
 * on top of the CSV baseline.
 */
public class CompetitionWorkspace {

    /**
     * One workspace per Vaadin session, stored as a session attribute so all
     * views see the same instance regardless of Spring DI availability (the
     * browserless tests run without a Spring context).
     */
    public static CompetitionWorkspace current() {
        VaadinSession session = VaadinSession.getCurrent();
        CompetitionWorkspace workspace = session.getAttribute(CompetitionWorkspace.class);
        if (workspace == null) {
            workspace = new CompetitionWorkspace(new StartPlanGenerator());
            session.setAttribute(CompetitionWorkspace.class, workspace);
        }
        return workspace;
    }

    private final StartPlanGenerator generator;

    private final Map<String, Course> coursesByClass = new LinkedHashMap<>();
    private Registrations registrations;
    private String selectedDay;

    /** Editable competitor counts per class for the selected day. */
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private CompetitionParameters parameters = CompetitionParameters.defaults();
    private StartPlan plan;

    /** Named competitors for the draw, loaded after the plan is made. */
    private List<Entry> entries = List.of();
    private StartDraw draw;

    public CompetitionWorkspace(StartPlanGenerator generator) {
        this.generator = generator;
    }

    public void addCourses(Map<String, Course> parsed) {
        coursesByClass.putAll(parsed);
    }

    public Map<String, Course> coursesByClass() {
        return coursesByClass;
    }

    public void setRegistrations(Registrations registrations) {
        this.registrations = registrations;
        selectedDay = null;
        counts.clear();
    }

    public Registrations registrations() {
        return registrations;
    }

    public boolean hasRegistrations() {
        return registrations != null;
    }

    public void selectDay(String day) {
        this.selectedDay = day;
        counts.clear();
        if (registrations != null && day != null) {
            counts.putAll(registrations.countsForDay(day));
        }
    }

    public String selectedDay() {
        return selectedDay;
    }

    public Map<String, Integer> counts() {
        return counts;
    }

    public void setCount(String className, int count) {
        counts.put(className, Math.max(0, count));
    }

    public void setParameters(CompetitionParameters parameters) {
        this.parameters = parameters;
    }

    public CompetitionParameters parameters() {
        return parameters;
    }

    public StartPlan plan() {
        return plan;
    }

    /** Changing the plan moves the start slots, so an earlier draw is discarded. */
    public void setPlan(StartPlan plan) {
        this.plan = plan;
        this.draw = null;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.draw = null;
    }

    public List<Entry> entries() {
        return entries;
    }

    public StartDraw draw() {
        return draw;
    }

    public void setDraw(StartDraw draw) {
        this.draw = draw;
    }

    /**
     * Wipes the workspace back to a fresh state so the user can start a new
     * competition without reloading the page. Keeps the default planner
     * parameters around but everything else (courses, counts, registrations,
     * plan) is cleared.
     */
    public void reset() {
        coursesByClass.clear();
        counts.clear();
        registrations = null;
        selectedDay = null;
        parameters = CompetitionParameters.defaults();
        plan = null;
        entries = List.of();
        draw = null;
    }

    /**
     * Captures the current state for JSON persistence. The raw {@link Registrations}
     * are intentionally left out — the editable {@code counts} carry forward all
     * information the user can still adjust.
     */
    public WorkspaceSnapshot snapshot() {
        return new WorkspaceSnapshot(coursesByClass, counts, selectedDay, parameters, plan);
    }

    /** Replaces all workspace state from a previously saved snapshot. */
    public void hydrate(WorkspaceSnapshot snapshot) {
        coursesByClass.clear();
        coursesByClass.putAll(snapshot.coursesByClass());
        counts.clear();
        counts.putAll(snapshot.counts());
        selectedDay = snapshot.selectedDay();
        parameters = snapshot.parameters() != null
                ? snapshot.parameters() : CompetitionParameters.defaults();
        setPlan(snapshot.plan());
        registrations = null; // user can re-upload CSV if they want to switch day
    }

    /**
     * Builds {@link Series} from the current counts and runs the planner.
     * Classes without a known course are returned separately so the caller
     * can warn the user.
     *
     * @return list of class names that had no matching course (empty if all matched)
     */
    public List<String> regeneratePlan() {
        List<Series> series = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int interval = parameters.defaultStartIntervalMinutes();

        counts.forEach((className, count) -> {
            if (count == null || count <= 0) {
                return;
            }
            Course course = coursesByClass.get(className);
            if (course == null) {
                missing.add(className);
            } else {
                series.add(new Series(className, course, count, interval));
            }
        });

        setPlan(generator.generate(series, parameters));
        return missing;
    }
}
