package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * An error or a warning the user must not miss: shown in the middle of the
 * screen and kept open until the user clicks OK. Routine confirmations stay
 * as plain {@link Notification#show(String)} toasts.
 */
public class ProblemNotification extends Notification {

    private ProblemNotification(String message, NotificationVariant variant) {
        addThemeVariants(variant);
        setPosition(Position.MIDDLE);
        setDuration(0);
        add(new Content(message));
    }

    public static ProblemNotification error(String message) {
        return open(new ProblemNotification(message, NotificationVariant.LUMO_ERROR));
    }

    public static ProblemNotification warning(String message) {
        return open(new ProblemNotification(message, NotificationVariant.LUMO_WARNING));
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
