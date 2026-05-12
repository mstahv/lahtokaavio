package in.virit.vlp.unit;

import static org.assertj.core.api.Assertions.assertThat;

import in.virit.vlp.LaneItem;
import in.virit.vlp.LanePlan;
import in.virit.vlp.VisualLanePlanner;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java unit tests for the {@link VisualLanePlanner} tester API. These
 * don't need a Vaadin UI — the component's mutating methods (move/lane queries)
 * operate on its own model independently of rendering.
 */
class VisualLanePlannerTest {

    @Test
    void firstStartOfRespectsLaneOffsetAndPriorItems() {
        VisualLanePlanner planner = new VisualLanePlanner();
        planner.setPlan(samplePlan(), p -> { });

        // LANE-1 hosts alpha (5 × 2 min) then beta (3 × 2 min after gap 2 × 2 = 4 min).
        // alpha starts at 11:00, ends at 11:08; lane free at 11:10; +4 min gap = 11:14
        assertThat(planner.firstStartOf("alpha")).isEqualTo(LocalTime.of(11, 0));
        assertThat(planner.firstStartOf("beta")).isEqualTo(LocalTime.of(11, 14));
        assertThat(planner.lastStartOf("beta")).isEqualTo(LocalTime.of(11, 18));

        // LANE-3 has offset 1 min → delta starts at 11:01
        assertThat(planner.firstStartOf("delta")).isEqualTo(LocalTime.of(11, 1));
    }

    @Test
    void moveItemToLaneUpdatesQueriesAndFiresCallback() {
        VisualLanePlanner planner = new VisualLanePlanner();
        AtomicReference<LanePlan> captured = new AtomicReference<>();
        planner.setPlan(samplePlan(), captured::set);

        planner.moveItemToLane("alpha", "LANE-2");

        assertThat(planner.laneOf("alpha")).isEqualTo("LANE-2");
        // LANE-2 previously had gamma alone (4 × 2 min). Appending alpha pushes
        // its first start past gamma's end + gap.
        assertThat(planner.firstStartOf("gamma")).isEqualTo(LocalTime.of(11, 0));
        assertThat(planner.firstStartOf("alpha"))
                .isAfter(LocalTime.of(11, 6)); // gamma ends 11:06, then gap

        // beta on LANE-1 now starts at the lane head (no preceding alpha).
        assertThat(planner.firstStartOf("beta")).isEqualTo(LocalTime.of(11, 0));

        // Callback fired with the updated plan.
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().items()).extracting(LaneItem::id)
                .contains("alpha", "beta", "gamma", "delta");
    }

    @Test
    void moveToSameLaneIsNoOp() {
        VisualLanePlanner planner = new VisualLanePlanner();
        AtomicReference<LanePlan> captured = new AtomicReference<>();
        planner.setPlan(samplePlan(), captured::set);

        planner.moveItemToLane("alpha", "LANE-1");

        assertThat(planner.laneOf("alpha")).isEqualTo("LANE-1");
        assertThat(captured.get()).isNull();
    }

    @Test
    void moveItemInsertsAtRequestedIndexAcrossLanes() {
        VisualLanePlanner planner = new VisualLanePlanner();
        planner.setPlan(samplePlan(), p -> { });

        // alpha → LANE-2 at index 0 (before gamma)
        planner.moveItem("alpha", "LANE-2", 0);

        assertThat(planner.laneOf("alpha")).isEqualTo("LANE-2");
        assertThat(planner.itemIds()).containsSubsequence("alpha", "gamma");
        // alpha now starts at lane offset (0), pushing gamma later.
        assertThat(planner.firstStartOf("alpha")).isEqualTo(LocalTime.of(11, 0));
        assertThat(planner.firstStartOf("gamma")).isAfter(LocalTime.of(11, 0));
    }

    @Test
    void moveItemWithinSameLaneReordersWithoutGap() {
        VisualLanePlanner planner = new VisualLanePlanner();
        planner.setPlan(samplePlan(), p -> { });

        // LANE-1 starts as [alpha, beta]; reverse to [beta, alpha]
        planner.moveItem("beta", "LANE-1", 0);

        assertThat(planner.itemIds()).containsSubsequence("beta", "alpha");
        assertThat(planner.firstStartOf("beta")).isEqualTo(LocalTime.of(11, 0));
        // alpha is now the trailing item; its first start follows beta + gap.
        assertThat(planner.firstStartOf("alpha")).isAfter(planner.lastStartOf("beta"));
    }

    @Test
    void moveItemToSameIndexInSameLaneIsNoOp() {
        VisualLanePlanner planner = new VisualLanePlanner();
        AtomicReference<LanePlan> captured = new AtomicReference<>();
        planner.setPlan(samplePlan(), captured::set);

        // alpha is currently at index 0 on LANE-1 — request the same.
        planner.moveItem("alpha", "LANE-1", 0);

        assertThat(captured.get()).isNull();
        assertThat(planner.itemIds()).containsExactly("alpha", "beta", "gamma", "delta");
    }

    @Test
    void itemIdsReturnedInRenderOrderPerLane() {
        VisualLanePlanner planner = new VisualLanePlanner();
        planner.setPlan(samplePlan(), p -> { });

        // LANE-1: alpha, beta; LANE-2: gamma; LANE-3: delta
        assertThat(planner.itemIds()).containsExactly("alpha", "beta", "gamma", "delta");
    }

    private static LanePlan samplePlan() {
        return new LanePlan(
                LocalTime.of(11, 0),
                2,
                List.of("LANE-1", "LANE-2", "LANE-3"),
                Map.of("LANE-1", 0, "LANE-2", 0, "LANE-3", 1),
                List.of(
                        new LaneItem("alpha", "LANE-1", "Alpha", 2, 5),
                        new LaneItem("beta", "LANE-1", "Beta", 2, 3),
                        new LaneItem("gamma", "LANE-2", "Gamma", 2, 4),
                        new LaneItem("delta", "LANE-3", "Delta", 1, 6)));
    }
}
