package in.virit.vlp;

/**
 * One item placed on a lane. Domain-agnostic: in an orienteering plan it
 * maps to a "series" (class) but the component itself doesn't care.
 *
 * @param id           stable unique id used by the tester API and DnD payload
 * @param laneId       which lane the item currently belongs to
 * @param label        display label
 * @param stepMinutes  gap between successive units within this item
 * @param unitCount    how many units (e.g. competitors) the item contains —
 *                     drives both the visual width and the inter-item gap
 * @param colorKey     optional grouping key for colour assignment. Items
 *                     sharing the same {@code colorKey} get the same
 *                     background colour. {@code null} falls back to {@code id}
 *                     so each item is uniquely coloured by default.
 * @param tooltipExtra optional extra string appended to the box's title
 *                     tooltip. Useful for surfacing one or two pieces of
 *                     domain context (e.g. "first control: 144") without
 *                     adding a dedicated field to the component.
 */
public record LaneItem(
        String id,
        String laneId,
        String label,
        int stepMinutes,
        int unitCount,
        String colorKey,
        String tooltipExtra) {

    /** Convenience constructor for the common case (no colour grouping, no extras). */
    public LaneItem(String id, String laneId, String label, int stepMinutes, int unitCount) {
        this(id, laneId, label, stepMinutes, unitCount, null, null);
    }
}
