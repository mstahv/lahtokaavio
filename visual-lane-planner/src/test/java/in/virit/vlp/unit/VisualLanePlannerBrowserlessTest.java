package in.virit.vlp.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.BrowserlessApplicationContext;
import com.vaadin.browserless.BrowserlessUIContext;
import com.vaadin.flow.component.html.Div;
import in.virit.vlp.BasicVisualLanePlannerUsageUI;
import in.virit.vlp.LaneItem;
import in.virit.vlp.LanePlan;
import in.virit.vlp.VisualLanePlanner;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Browserless integration of the demo UI: navigates to
 * {@link BasicVisualLanePlannerUsageUI} and verifies the render output via the
 * {@code data-item} / {@code data-lane-track} attributes the component adds.
 * <p>
 * Moves go through the component's public API
 * ({@link VisualLanePlanner#moveItemToLane}), the same code path the drop
 * listener and the context menu use. The drag-and-drop gesture can't be
 * simulated browserless, and a target's closed context menu can't be found
 * with a locator yet (not attached to the UI until opened).
 */
class VisualLanePlannerBrowserlessTest {

    private BrowserlessApplicationContext app;
    private BrowserlessUIContext window;

    @BeforeEach
    void setUp() {
        app = BrowserlessApplicationContext.create(BasicVisualLanePlannerUsageUI.class);
        window = app.newUser().newWindow();
    }

    @AfterEach
    void tearDown() {
        app.close();
    }

    @Test
    void rendersExpectedItemsAndLanes() {
        VisualLanePlanner planner = window.navigate(BasicVisualLanePlannerUsageUI.class).getPlanner();

        assertThat(planner.itemIds()).containsExactly("alpha", "beta", "gamma", "delta");

        // The DOM should expose data-item attributes for each item box.
        assertThat(window.findDiv().withAttribute("data-item").components()).hasSize(4);
    }

    @Test
    void itemsSharingColorKeyRenderWithSameBackgroundColor() {
        VisualLanePlanner planner = window.navigate(BasicVisualLanePlannerUsageUI.class).getPlanner();

        // The demo plan uses default 5-arg constructor so colorKey=null and
        // every item gets a unique colour. Swap in a custom plan where two
        // items share a key and verify the rendered colour matches.
        planner.setPlan(new LanePlan(
                LocalTime.of(11, 0),
                2,
                List.of("LANE-1", "LANE-2"),
                Map.of("LANE-1", 0, "LANE-2", 0),
                List.of(
                        new LaneItem("a", "LANE-1", "A", 2, 3, 144, null),
                        new LaneItem("b", "LANE-2", "B", 2, 3, 144, null),
                        new LaneItem("c", "LANE-1", "C", 2, 3, 100, null))),
                p -> { });

        String colorA = backgroundColorOf("a");
        String colorB = backgroundColorOf("b");
        String colorC = backgroundColorOf("c");

        assertThat(colorA).isEqualTo(colorB);  // same key → same colour
        assertThat(colorA).isNotEqualTo(colorC); // different key → different colour
    }

    @Test
    void moveItemToLaneIsReflectedInBothModelAndRenderedDom() {
        VisualLanePlanner planner = window.navigate(BasicVisualLanePlannerUsageUI.class).getPlanner();

        // Shortcut: the component's API instead of a drag gesture
        planner.moveItemToLane("alpha", "LANE-2");

        assertThat(planner.laneOf("alpha")).isEqualTo("LANE-2");
        // The alpha box now lives inside the LANE-2 track in the DOM.
        assertThat(laneTrackOf("alpha")).isEqualTo("LANE-2");
    }

    @Test
    void movedItemKeepsItsColourAndTooltipExtras() {
        VisualLanePlanner planner = window.navigate(BasicVisualLanePlannerUsageUI.class).getPlanner();
        planner.setPlan(new LanePlan(
                LocalTime.of(11, 0),
                2,
                List.of("LANE-1", "LANE-2"),
                Map.of("LANE-1", 0, "LANE-2", 0),
                List.of(
                        new LaneItem("a", "LANE-1", "A", 2, 3, 144, "first control: 144"),
                        new LaneItem("b", "LANE-1", "B", 2, 3, 100, "first control: 100"))),
                p -> { });
        String colorBefore = backgroundColorOf("b");

        // Reorder within the lane, then move across lanes
        planner.moveItem("b", "LANE-1", 0);
        assertThat(backgroundColorOf("b")).isEqualTo(colorBefore);
        assertThat(titleOf("b")).contains("first control: 100");

        planner.moveItemToLane("b", "LANE-2");
        assertThat(backgroundColorOf("b")).isEqualTo(colorBefore);
        assertThat(titleOf("b")).contains("first control: 100");
    }

    @Test
    void itemWarningsOutlineTheBoxesUntilCleared() {
        VisualLanePlanner planner = window.navigate(BasicVisualLanePlannerUsageUI.class).getPlanner();

        planner.setItemWarnings(Map.of("alpha", "Clashes with gamma"));
        assertThat(window.findDiv().withAttribute("data-warning").components())
                .extracting(box -> box.getElement().getAttribute("data-item"))
                .containsExactly("alpha");
        assertThat(titleOf("alpha")).endsWith("⚠ Clashes with gamma");
        assertThat(planner.warningOf("alpha")).isEqualTo("Clashes with gamma");

        planner.setItemWarnings(Map.of());
        assertThat(window.findDiv().withAttribute("data-warning").exists()).isFalse();
        assertThat(titleOf("alpha")).doesNotContain("⚠");
    }

    private String titleOf(String itemId) {
        return window.findDiv().withAttribute("data-item", itemId).component()
                .getElement().getAttribute("title");
    }

    private String backgroundColorOf(String itemId) {
        return window.findDiv().withAttribute("data-item", itemId).component()
                .getStyle().get("background-color");
    }

    private String laneTrackOf(String itemId) {
        Div box = window.findDiv().withAttribute("data-item", itemId).component();
        return box.getParent().orElseThrow().getElement().getAttribute("data-lane-track");
    }
}
