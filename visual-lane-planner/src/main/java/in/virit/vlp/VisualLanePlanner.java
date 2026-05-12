package in.virit.vlp;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropEffect;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.dnd.EffectAllowed;
import com.vaadin.flow.dom.Style;
import in.virit.color.Color;
import in.virit.color.HslColor;
import in.virit.color.NamedColor;
import org.vaadin.firitin.util.VStyle;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.vaadin.firitin.components.html.VDiv;
import org.vaadin.firitin.components.html.VSpan;
import org.vaadin.firitin.components.orderedlayout.VHorizontalLayout;
import org.vaadin.firitin.components.orderedlayout.VVerticalLayout;

/**
 * Gantt-style visualisation and arrangement editor for time-anchored items
 * placed into parallel lanes. Drag an item onto another lane, drop it between
 * two siblings, or use its "…" menu — all three routes funnel through
 * {@link #moveItem(String, String, int)}, which tests can drive directly.
 *
 * <p>Rendering is broken into small inner component classes:
 * {@link TimeAxisRow}, {@link LaneRow}, {@link LaneTrack}, {@link ItemBox},
 * {@link ItemMenu}. Each takes care of its own styling and event wiring.
 * The drag-and-drop sources/targets implement {@link DragSource} /
 * {@link DropTarget} directly rather than relying on
 * {@code DragSource.create(...)} configurators — the inheritance form keeps
 * the constructor calls flat and the lifecycle easy to follow.
 *
 * <h3>Tester API</h3>
 * <ul>
 *   <li>{@link #moveItem(String, String, int)} – the canonical mutator</li>
 *   <li>{@link #moveItemToLane(String, String)} – convenience append</li>
 *   <li>{@link #laneIds()} / {@link #laneOf(String)} – queries</li>
 *   <li>{@link #firstStartOf(String)} / {@link #lastStartOf(String)} – computed times</li>
 *   <li>{@link #itemIds()} – every known item id, in render order</li>
 * </ul>
 * Browserless tests should prefer these over reaching into the rendered DOM
 * (menu structure and pixel positions are intentionally unstable).
 */
public class VisualLanePlanner extends Composite<VVerticalLayout> {

    private static final int PIXELS_PER_MINUTE = 8;
    private static final int LANE_LABEL_WIDTH_PX = 80;
    private static final int LANE_HEIGHT_PX = 32;
    private static final int ITEM_BOX_INSET_PX = 4;

    private static final Color LANE_TRACK_BG = NamedColor.WHITESMOKE;
    private static final Color ITEM_BORDER = NamedColor.DIMGRAY;
    private static final Color DROP_INDICATOR_FILL = NamedColor.DODGERBLUE;

    /**
     * Picks a stable background colour for an item. Items sharing the same
     * {@code colorKey} get the same colour — so the user can spot at a glance
     * which series compete for the same first slot.
     *
     * <p>The stride of 71° is coprime with 360, so adjacent numeric keys
     * (e.g. control codes 164 and 165) land roughly a quarter of the wheel
     * apart instead of looking near-identical.
     */
    private static Color colorFor(LaneItem item) {
        int h = Math.floorMod(item.colorKey() * 33, 360);
        return new HslColor(h, 65, 78);
    }

    private LanePlan plan;
    private final LinkedHashMap<String, List<LaneItem>> byLane = new LinkedHashMap<>();
    private Consumer<LanePlan> onChange;

    public VisualLanePlanner() {
        getContent().setSpacing(false);
        getContent().setPadding(false);
        getContent().getElement().setAttribute("data-component", "visual-lane-planner");
    }

    /**
     * Loads a plan into the component. The {@code onChange} callback fires
     * with the latest snapshot after every mutating operation; the caller
     * typically persists it back to its own state holder.
     */
    public void setPlan(LanePlan plan, Consumer<LanePlan> onChange) {
        this.plan = Objects.requireNonNull(plan);
        this.onChange = onChange;
        rebuildByLane();
        render();
    }

    private void rebuildByLane() {
        byLane.clear();
        for (String laneId : plan.laneIds()) {
            byLane.put(laneId, new ArrayList<>());
        }
        // Items not in declared lanes still get a bucket so they never get
        // dropped silently.
        for (LaneItem item : plan.items()) {
            byLane.computeIfAbsent(item.laneId(), k -> new ArrayList<>()).add(item);
        }
    }

    // --- Tester API -------------------------------------------------------

    public List<String> laneIds() {
        return new ArrayList<>(byLane.keySet());
    }

    public List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        byLane.values().forEach(lane -> lane.forEach(item -> ids.add(item.id())));
        return ids;
    }

    public String laneOf(String itemId) {
        for (var entry : byLane.entrySet()) {
            for (LaneItem item : entry.getValue()) {
                if (item.id().equals(itemId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public LocalTime firstStartOf(String itemId) {
        for (var entry : byLane.entrySet()) {
            int cursor = laneOffsetMinutes(entry.getKey());
            boolean first = true;
            for (LaneItem item : entry.getValue()) {
                if (!first) {
                    cursor += plan.gapBetweenItemsInSteps() * item.stepMinutes();
                }
                if (item.id().equals(itemId)) {
                    return plan.origin().plusMinutes(cursor);
                }
                cursor += item.unitCount() * item.stepMinutes();
                first = false;
            }
        }
        return null;
    }

    public LocalTime lastStartOf(String itemId) {
        LocalTime first = firstStartOf(itemId);
        if (first == null) {
            return null;
        }
        for (List<LaneItem> lane : byLane.values()) {
            for (LaneItem item : lane) {
                if (item.id().equals(itemId)) {
                    return first.plusMinutes((long) (item.unitCount() - 1) * item.stepMinutes());
                }
            }
        }
        return null;
    }

    /**
     * Moves an item to the end of another lane. Calling this with the
     * item's current lane is a no-op (use {@link #moveItem} with an
     * explicit index to reorder within a single lane).
     */
    public void moveItemToLane(String itemId, String targetLaneId) {
        if (targetLaneId.equals(laneOf(itemId))) {
            return;
        }
        moveItem(itemId, targetLaneId, -1);
    }

    /**
     * Moves an item to a target lane at a specific index. Use {@code -1} (or
     * any value past the lane's size) to append at the end.
     *
     * <p>Same code path used by drop listeners and the "…" menu; tests can
     * call this method to verify ordering without simulating drag events.
     */
    public void moveItem(String itemId, String targetLaneId, int insertIndex) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(targetLaneId);
        if (plan == null) {
            return;
        }
        if (!byLane.containsKey(targetLaneId)) {
            throw new IllegalArgumentException("Unknown lane: " + targetLaneId);
        }

        Locator source = locate(itemId);
        if (source == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }

        int targetSize = byLane.get(targetLaneId).size();
        int normalised = (insertIndex < 0 || insertIndex > targetSize) ? targetSize : insertIndex;
        if (source.laneId().equals(targetLaneId)) {
            int adjusted = source.index() < normalised ? normalised - 1 : normalised;
            if (adjusted == source.index()) {
                return; // already in place
            }
        }

        LaneItem moved = byLane.get(source.laneId()).remove(source.index());
        int finalSize = byLane.get(targetLaneId).size();
        int finalIndex;
        if (insertIndex < 0 || insertIndex > finalSize) {
            finalIndex = finalSize;
        } else if (source.laneId().equals(targetLaneId) && source.index() < insertIndex) {
            finalIndex = insertIndex - 1;
        } else {
            finalIndex = insertIndex;
        }

        byLane.get(targetLaneId).add(finalIndex, new LaneItem(
                moved.id(), targetLaneId, moved.label(),
                moved.stepMinutes(), moved.unitCount()));

        plan = currentSnapshot();
        render();
        if (onChange != null) {
            onChange.accept(plan);
        }
    }

    private Locator locate(String itemId) {
        for (var entry : byLane.entrySet()) {
            List<LaneItem> lane = entry.getValue();
            for (int i = 0; i < lane.size(); i++) {
                if (lane.get(i).id().equals(itemId)) {
                    return new Locator(entry.getKey(), i);
                }
            }
        }
        return null;
    }

    private LanePlan currentSnapshot() {
        List<LaneItem> ordered = new ArrayList<>();
        byLane.values().forEach(ordered::addAll);
        LinkedHashSet<String> laneIdSet = new LinkedHashSet<>(plan.laneIds());
        laneIdSet.addAll(byLane.keySet());
        return new LanePlan(plan.origin(), plan.gapBetweenItemsInSteps(),
                new ArrayList<>(laneIdSet), plan.laneOffsetMinutes(), ordered);
    }

    private int laneOffsetMinutes(String laneId) {
        Integer v = plan.laneOffsetMinutes().get(laneId);
        return v == null ? 0 : v;
    }

    private int chartSpanMinutes() {
        int max = 0;
        for (var entry : byLane.entrySet()) {
            int cursor = laneOffsetMinutes(entry.getKey());
            boolean first = true;
            for (LaneItem item : entry.getValue()) {
                if (!first) {
                    cursor += plan.gapBetweenItemsInSteps() * item.stepMinutes();
                }
                int lastStartMinutes = cursor + (item.unitCount() - 1) * item.stepMinutes();
                if (lastStartMinutes > max) {
                    max = lastStartMinutes;
                }
                cursor += item.unitCount() * item.stepMinutes();
                first = false;
            }
        }
        return max;
    }

    private void render() {
        getContent().removeAll();
        if (plan == null) {
            return;
        }
        getContent().add(new TimeAxisRow());
        for (String laneId : plan.laneIds()) {
            getContent().add(new LaneRow(laneId));
        }
    }

    private record Locator(String laneId, int index) {}

    // ---------------------------------------------------------------------
    // Visual sub-components — each owns its own styling and event wiring.
    // ---------------------------------------------------------------------

    /** Top row with the "Time" label and the 5-minute tick marks. */
    private class TimeAxisRow extends VHorizontalLayout {
        TimeAxisRow() {
            setSpacing(false);
            setPadding(false);
            VSpan label = new VSpan("Time");
            label.getStyle().setWidthPx(LANE_LABEL_WIDTH_PX);
            add(label, new TimeAxisTrack());
        }
    }

    private class TimeAxisTrack extends VDiv {
        TimeAxisTrack() {
            int spanMinutes = chartSpanMinutes() + 1;
            VStyle style = getStyle();
            style.setPosition(Style.Position.RELATIVE);
            style.setHeightPx(20);
            style.setWidthPx(spanMinutes * PIXELS_PER_MINUTE);
            for (int m = 0; m <= spanMinutes; m += 5) {
                add(new TimeTick(m));
            }
        }
    }

    private class TimeTick extends VSpan {
        TimeTick(int minute) {
            super(plan.origin().plusMinutes(minute).toString());
            VStyle style = getStyle();
            style.setPosition(Style.Position.ABSOLUTE);
            style.setLeftPx(minute * PIXELS_PER_MINUTE);
            style.setFontSize("11px");
        }
    }

    /** One horizontal row: lane id label on the left, items track on the right. */
    private class LaneRow extends VHorizontalLayout {
        LaneRow(String laneId) {
            setSpacing(false);
            setPadding(false);
            getElement().setAttribute("data-lane", laneId);

            VSpan label = new VSpan(laneId);
            label.getStyle().setWidthPx(LANE_LABEL_WIDTH_PX);
            add(label, new LaneTrack(laneId));
        }
    }

    /**
     * The horizontal strip where items are placed for one lane. Acts as a
     * drop target; the cursor's x-offset on drop is mapped to an insert
     * index so users can land an item between siblings or at the head.
     */
    private class LaneTrack extends VDiv implements DropTarget<LaneTrack> {
        private final String laneId;

        LaneTrack(String laneId) {
            this.laneId = laneId;
            addClassName("vlp-lane-track");
            getElement().setAttribute("data-lane-track", laneId);
            VStyle style = getStyle();
            style.setPosition(Style.Position.RELATIVE);
            style.setHeightPx(LANE_HEIGHT_PX);
            style.setBackgroundColor(LANE_TRACK_BG);
            style.setMinWidthPx(400);

            setActive(true);
            setDropEffect(DropEffect.MOVE);
            addDropListener(event -> event.getDragData().ifPresent(data -> {
                if (data instanceof String itemId) {
                    int insertIndex = insertIndexForCursor(event.getOffsetX(), itemId);
                    moveItem(itemId, laneId, insertIndex);
                }
            }));

            add(new DropIndicator());
            wireDropIndicator();
            populate();
        }

        /**
         * Pure-client-side dragover/leave/drop wiring that moves a vertical
         * indicator bar to the "insertion line" closest to the cursor. Runs
         * locally in the browser so feedback is instant; no server roundtrip
         * per mouse-move.
         */
        private void wireDropIndicator() {
            getElement().executeJs("""
                    const track = this;
                    const indicator = track.querySelector('[data-drop-indicator]');
                    function place(e) {
                      const rect = track.getBoundingClientRect();
                      const cursorX = e.clientX - rect.left;
                      const items = Array.from(track.querySelectorAll('[data-item]'));
                      let insertX = 0;
                      for (const it of items) {
                        const left = it.offsetLeft;
                        const width = it.offsetWidth;
                        if (cursorX < left + width / 2) {
                          indicator.style.left = (left - 1) + 'px';
                          indicator.style.display = 'block';
                          return;
                        }
                        insertX = left + width;
                      }
                      indicator.style.left = insertX + 'px';
                      indicator.style.display = 'block';
                    }
                    track.addEventListener('dragover', place);
                    track.addEventListener('dragenter', place);
                    track.addEventListener('dragleave', function(e) {
                      // Only hide if the cursor truly left this track — dragleave
                      // also fires when moving onto a child.
                      if (e.target === track) indicator.style.display = 'none';
                    });
                    track.addEventListener('drop', function() {
                      indicator.style.display = 'none';
                    });
                    """);
        }

        private void populate() {
            int cursor = laneOffsetMinutes(laneId);
            boolean first = true;
            for (LaneItem item : byLane.getOrDefault(laneId, List.of())) {
                if (!first) {
                    cursor += plan.gapBetweenItemsInSteps() * item.stepMinutes();
                }
                add(new ItemBox(item, cursor));
                cursor += item.unitCount() * item.stepMinutes();
                first = false;
            }
        }

        /**
         * Maps a horizontal cursor offset (pixels from the track's left edge)
         * to an insertion index in this lane. The dragged item's own slot is
         * skipped so a drop near its current spot doesn't bounce it one over.
         */
        private int insertIndexForCursor(int offsetX, String draggedItemId) {
            List<LaneItem> lane = byLane.getOrDefault(laneId, List.of());
            if (lane.isEmpty()) {
                return 0;
            }
            int cursorMinutes = offsetX / PIXELS_PER_MINUTE;
            int rendererCursor = laneOffsetMinutes(laneId);
            boolean first = true;
            int index = 0;
            for (LaneItem item : lane) {
                if (!first) {
                    rendererCursor += plan.gapBetweenItemsInSteps() * item.stepMinutes();
                }
                int itemLeft = rendererCursor;
                int itemRight = itemLeft + item.unitCount() * item.stepMinutes();
                if (cursorMinutes < itemLeft) {
                    return index;
                }
                if (cursorMinutes < itemRight && !item.id().equals(draggedItemId)) {
                    int midpoint = (itemLeft + itemRight) / 2;
                    return cursorMinutes < midpoint ? index : index + 1;
                }
                rendererCursor = itemRight;
                first = false;
                index++;
            }
            return lane.size();
        }
    }

    /**
     * One time-anchored item. The box doubles as a {@link DragSource}; a
     * right-click / long-press opens an {@link ItemContextMenu} as the
     * keyboard- and touch-friendly alternative to dragging. The dots-menu
     * used to live inside the box; moving it to a context menu reclaims that
     * real estate for the label.
     */
    private class ItemBox extends VDiv implements DragSource<ItemBox> {
        ItemBox(LaneItem item, int firstStartMinutes) {
            int widthMinutes = item.unitCount() * item.stepMinutes();
            addClassName("vlp-item-box");
            getElement().setAttribute("data-item", item.id());
            getElement().setAttribute("title", tooltipFor(item));
            VStyle style = getStyle();
            style.setPosition(Style.Position.ABSOLUTE);
            style.setLeftPx(firstStartMinutes * PIXELS_PER_MINUTE);
            style.setTopPx(2);
            style.setWidthPx(widthMinutes * PIXELS_PER_MINUTE);
            style.setHeightPx(LANE_HEIGHT_PX - ITEM_BOX_INSET_PX);
            style.setBackgroundColor(colorFor(item));
            style.setBorder("1px solid " + ITEM_BORDER);
            style.setBorderRadius("3px");
            style.setPadding("0 4px");
            style.setBoxSizing(Style.BoxSizing.BORDER_BOX);
            style.setDisplay(Style.Display.FLEX);
            style.setAlignItems(Style.AlignItems.CENTER);
            style.setOverflow(Style.Overflow.HIDDEN);
            style.setCursor("grab");

            add(new ItemLabel(item));
            new ItemContextMenu(this, item);

            setDraggable(true);
            setEffectAllowed(EffectAllowed.MOVE);
            setDragData(item.id());
        }
    }

    private String tooltipFor(LaneItem item) {
        StringBuilder b = new StringBuilder(item.label());
        LocalTime first = firstStartOf(item.id());
        if (first != null) {
            LocalTime last = first.plusMinutes((long) (item.unitCount() - 1) * item.stepMinutes());
            b.append(" — ").append(item.unitCount()).append(" starters at ")
                    .append(item.stepMinutes()).append(" min interval, ")
                    .append(first).append(" … ").append(last);
        }
        if (item.tooltipExtra() != null && !item.tooltipExtra().isBlank()) {
            b.append("\n").append(item.tooltipExtra());
        }
        return b.toString();
    }

    /**
     * Thin vertical bar shown by the dragover handler at the "would-insert
     * here" position. Hidden by default; positioned absolutely inside the
     * lane track. {@code pointer-events: none} keeps it from interfering
     * with the drag event itself.
     */
    private static class DropIndicator extends VDiv {
        DropIndicator() {
            getElement().setAttribute("data-drop-indicator", "");
            VStyle style = getStyle();
            style.setPosition(Style.Position.ABSOLUTE);
            style.setTopPx(0);
            style.setHeight("100%");
            style.setWidthPx(3);
            style.setBackgroundColor(DROP_INDICATOR_FILL);
            style.setDisplay(Style.Display.NONE);
            style.set("pointer-events", "none");
        }
    }

    /** Item label — ellipsis-truncated so narrow boxes stay readable. */
    private static class ItemLabel extends VSpan {
        ItemLabel(LaneItem item) {
            super(item.label());
            VStyle style = getStyle();
            style.setWhiteSpace(Style.WhiteSpace.NOWRAP);
            style.setOverflow(Style.Overflow.HIDDEN);
            style.set("text-overflow", "ellipsis");
            style.set("flex", "1 1 auto");
        }
    }

    /**
     * Context menu attached to an item box — opens on right-click (desktop)
     * or long-press (touch). Two submenus:
     * <ul>
     *   <li><b>Move to lane</b> — append to the end of another lane
     *       (covers empty target lanes too)</li>
     *   <li><b>Move before</b> — insert directly before any other item,
     *       useful for both same-lane reordering and cross-lane inserts
     *       at a specific position</li>
     * </ul>
     */
    private class ItemContextMenu extends ContextMenu {
        ItemContextMenu(ItemBox host, LaneItem item) {
            super(host);
            getElement().setAttribute("data-menu-for", item.id());
            addMoveToLane(item);
            addMoveBefore(item);
        }

        private void addMoveToLane(LaneItem item) {
            MenuItem parent = addItem("Move to lane");
            for (Map.Entry<String, List<LaneItem>> e : byLane.entrySet()) {
                String laneId = e.getKey();
                if (laneId.equals(item.laneId())) {
                    continue;
                }
                MenuItem entry = parent.getSubMenu().addItem(laneId,
                        ev -> moveItemToLane(item.id(), laneId));
                entry.getElement().setAttribute("data-target-lane", laneId);
            }
        }

        private void addMoveBefore(LaneItem item) {
            // Only siblings in the same lane: cross-lane moves already have
            // their own "Move to lane" entry, and listing every item in
            // every lane made the submenu overwhelming.
            List<LaneItem> sameLane = byLane.getOrDefault(item.laneId(), List.of());
            MenuItem parent = addItem("Move before");
            for (int i = 0; i < sameLane.size(); i++) {
                LaneItem target = sameLane.get(i);
                if (target.id().equals(item.id())) {
                    continue;
                }
                int index = i;
                MenuItem entry = parent.getSubMenu().addItem(target.label(),
                        ev -> moveItem(item.id(), item.laneId(), index));
                entry.getElement().setAttribute("data-before-item", target.id());
            }
        }
    }
}
