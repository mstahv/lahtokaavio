package in.virit.vlp.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessExtension;
import com.vaadin.browserless.TesterWrappers;
import com.vaadin.flow.component.html.Div;
import in.virit.vlp.BasicVisualLanePlannerUsageUI;
import in.virit.vlp.VisualLanePlanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Browserless integration of the demo UI: navigates to
 * {@link BasicVisualLanePlannerUsageUI}, then exercises the
 * {@link VisualLanePlanner} tester API. Verifies render output via the
 * {@code data-item} / {@code data-lane-track} attributes the component adds
 * — but the moves themselves go through the public API, not through the
 * generated MenuBar items (those would be an implementation detail).
 */
class VisualLanePlannerBrowserlessTest implements TesterWrappers {

    @RegisterExtension
    BrowserlessExtension ext = new BrowserlessExtension()
            .withViewPackages(BasicVisualLanePlannerUsageUI.class);

    @Test
    void rendersExpectedItemsAndLanes() {
        BasicVisualLanePlannerUsageUI view = ext.navigate(BasicVisualLanePlannerUsageUI.class);
        VisualLanePlanner planner = view.getPlanner();

        assertThat(planner.itemIds()).containsExactly("alpha", "beta", "gamma", "delta");

        // The DOM should expose data-item attributes for each item box.
        long boxCount = ext.$(Div.class).withAttribute("data-item").all().size();
        assertThat(boxCount).isEqualTo(4);
    }

    @Test
    void itemsSharingColorKeyRenderWithSameBackgroundColor() {
        ext.navigate(BasicVisualLanePlannerUsageUI.class);

        // The demo plan uses default 5-arg constructor so colorKey=null and
        // every item gets a unique colour. Swap in a custom plan where two
        // items share a key and verify the rendered colour matches.
        BasicVisualLanePlannerUsageUI view = ext.$(BasicVisualLanePlannerUsageUI.class).single();
        VisualLanePlanner planner = view.getPlanner();
        planner.setPlan(new in.virit.vlp.LanePlan(
                java.time.LocalTime.of(11, 0),
                2,
                java.util.List.of("LANE-1", "LANE-2"),
                java.util.Map.of("LANE-1", 0, "LANE-2", 0),
                java.util.List.of(
                        new in.virit.vlp.LaneItem("a", "LANE-1", "A", 2, 3, 144, null),
                        new in.virit.vlp.LaneItem("b", "LANE-2", "B", 2, 3, 144, null),
                        new in.virit.vlp.LaneItem("c", "LANE-1", "C", 2, 3, 100, null))),
                p -> { });

        String colorA = ext.$(Div.class).withAttribute("data-item", "a").single()
                .getStyle().get("background-color");
        String colorB = ext.$(Div.class).withAttribute("data-item", "b").single()
                .getStyle().get("background-color");
        String colorC = ext.$(Div.class).withAttribute("data-item", "c").single()
                .getStyle().get("background-color");

        assertThat(colorA).isEqualTo(colorB);  // same key → same colour
        assertThat(colorA).isNotEqualTo(colorC); // different key → different colour
    }

    @Test
    void moveItemToLaneIsReflectedInBothModelAndRenderedDom() {
        BasicVisualLanePlannerUsageUI view = ext.navigate(BasicVisualLanePlannerUsageUI.class);
        VisualLanePlanner planner = view.getPlanner();

        planner.moveItemToLane("alpha", "LANE-2");

        // Model query.
        assertThat(planner.laneOf("alpha")).isEqualTo("LANE-2");

        // The alpha box now lives inside the LANE-2 track in the DOM.
        Div alphaBox = ext.$(Div.class).withAttribute("data-item", "alpha").single();
        Div parent = (Div) alphaBox.getParent().orElseThrow();
        assertThat(parent.getElement().getAttribute("data-lane-track")).isEqualTo("LANE-2");
    }
}
