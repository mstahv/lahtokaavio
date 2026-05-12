# Visual Lane Planner

A Gantt-style Vaadin Flow component for arranging time-anchored items into
parallel lanes. Drag-and-drop or `…` menu both route through a single public
API, which is also the entry point for tests.

## Quick start

```java
LanePlan plan = new LanePlan(
        LocalTime.of(11, 0),
        2,                                              // gap between items in steps
        List.of("LANE-1", "LANE-2"),
        Map.of("LANE-1", 0, "LANE-2", 1),               // lane offset minutes from origin
        List.of(
                new LaneItem("alpha", "LANE-1", "Alpha", 2, 5),   // step=2 min, 5 units
                new LaneItem("beta",  "LANE-2", "Beta",  2, 3)));

VisualLanePlanner planner = new VisualLanePlanner();
planner.setPlan(plan, edited -> repository.save(edited));
```

`LaneItem` has two optional fields for richer presentation:

- `colorKey` — items sharing a key get the same background colour (orienteering
  uses the first control code, so all series heading to control 144 share a hue).
  Falls back to `id` when null.
- `tooltipExtra` — a short string appended to the title-attribute tooltip.

Use the 7-arg constructor when you want them:

```java
new LaneItem("h21e", "STA1-1", "H21E", 2, 20, "ctrl-144", "first control: 144");
```

## Tester API

The component exposes mutating + query methods so tests (and other code paths)
don't have to go through the rendered DOM:

| Method                              | What it does                          |
| ----------------------------------- | ------------------------------------- |
| `moveItem(itemId, laneId, index)`   | move + insert at lane-relative index (`-1` = append). Supports cross-lane and within-lane reorder. |
| `moveItemToLane(itemId, laneId)`    | append to a different lane (no-op if same lane)        |
| `laneOf(itemId)`                    | current lane                          |
| `firstStartOf(itemId)`              | derived from lane offset + ordering   |
| `lastStartOf(itemId)`               | first + (unitCount-1) × stepMinutes   |
| `itemIds()` / `laneIds()`           | render-order listings                 |

Drop-handler maps the cursor's horizontal offset to an `insertIndex` so the
user can land an item between two siblings or at a lane's head — not just at
the end.

See `src/test/java/in/virit/vlp/unit/VisualLanePlannerTest.java` for examples.

## Testing layers

| Folder           | Tooling                  | Purpose                            | Maven phase |
| ---------------- | ------------------------ | ---------------------------------- | ----------- |
| `unit/*Test.java`  | JUnit 5 + AssertJ          | Pure-Java unit tests of the API    | `test`      |
| `unit/*Browserless*Test.java` | Vaadin browserless-test | Render to mock UI, query DOM, drive moves via API | `test` |
| `it/*IT.java`      | Spring Boot + Playwright | Real Chromium, asserts no JS errors | `verify`    |

Run modes:

- `mvn test` — unit + browserless (fast, ~3 s)
- `mvn verify` — adds Playwright IT (~10–60 s on first run, downloads Chromium)

## Why not simulate drag in tests?

Drag-and-drop in a browserless environment can't fire the full HTML5 event
sequence. The component's drop handler immediately delegates to
`moveItemToLane(...)`; tests call that same method directly. Playwright IT
verifies the DOM and JS layer behave (no errors, items render) but stays out
of pixel-level drag simulation — fragile, low value.

This pattern is documented further in
`../docs/testing-custom-components.md`.
