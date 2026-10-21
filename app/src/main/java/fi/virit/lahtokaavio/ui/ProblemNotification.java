package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import fi.virit.lahtokaavio.domain.PlanReport;
import java.util.stream.Collectors;

/**
 * An error, a warning or an assumption the user must not miss: shown in the
 * middle of the screen and kept open until the user clicks OK. Routine
 * confirmations stay as plain {@link Notification#show(String)} toasts.
 */
public class ProblemNotification extends Notification {

    private ProblemNotification(String message, NotificationVariant variant) {
        addThemeVariants(variant);
        setPosition(Position.MIDDLE);
        setDuration(0);
        add(new Content(message));
    }

    public static ProblemNotification error(String message) {
        return open(new ProblemNotification(message, NotificationVariant.ERROR));
    }

    public static ProblemNotification warning(String message) {
        return open(new ProblemNotification(message, NotificationVariant.WARNING));
    }

    public static ProblemNotification info(String message) {
        return open(new ProblemNotification(message, NotificationVariant.INFO));
    }

    /** Tells about classes left out of a new plan, and planned with a borrowed course. */
    public static void showPlanReport(PlanReport report) {
        if (!report.missingCourses().isEmpty()) {
            warning("Warning: no course found for " + report.missingCourses());
        }
        if (!report.borrowedCourses().isEmpty()) {
            info(report.borrowedCourses().entrySet().stream()
                    .map(e -> e.getKey() + " has no course of its own, using course \""
                            + e.getValue() + "\"")
                    .collect(Collectors.joining(". ", "", ".")));
        }
    }

    private static ProblemNotification open(ProblemNotification notification) {
        notification.open();
        return notification;
    }

    private class Content extends HorizontalLayout {
        Content(String message) {
            setAlignItems(Alignment.CENTER);
            Paragraph text = new Paragraph(message);
            text.getStyle().setMaxWidth("40em");
            add(text, new Button("OK", e -> close()));
        }
    }
}
