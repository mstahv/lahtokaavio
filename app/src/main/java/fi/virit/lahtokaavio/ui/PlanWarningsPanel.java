package fi.virit.lahtokaavio.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.dom.Style;
import fi.virit.lahtokaavio.domain.PlanWarning;
import java.util.List;

/**
 * The rules the current plan breaks, typically after dragging series around
 * in the Gantt view. Stays on screen as long as the problems do, and is
 * hidden when the plan is fine.
 */
public class PlanWarningsPanel extends Div {

    private final H4 title = new H4();
    private final UnorderedList list = new UnorderedList();

    public PlanWarningsPanel() {
        getElement().setAttribute("role", "alert");
        getStyle()
                .setBorder("2px solid crimson")
                .setBorderRadius("6px")
                .setBackgroundColor("#fdecee")
                .setColor("#5c0011")
                .setPadding("0.5em 1em")
                .setBoxSizing(Style.BoxSizing.BORDER_BOX);
        setWidthFull();
        title.getStyle().setMargin("0");
        list.getStyle().setMargin("0.25em 0 0 0");
        add(title, list);
        setVisible(false);
    }

    public void show(List<PlanWarning> warnings) {
        list.removeAll();
        warnings.forEach(w -> list.add(new ListItem(w.message())));
        title.setText(warnings.size() == 1
                ? "⚠ The plan breaks a rule"
                : "⚠ The plan breaks " + warnings.size() + " rules");
        setVisible(!warnings.isEmpty());
    }
}
