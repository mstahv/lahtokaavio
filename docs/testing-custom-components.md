# Testing custom Vaadin components in browserless tests

Lessons from building and testing `VisualLanePlanner`, a Gantt-style
component where users arrange items into lanes by drag and drop or from a
context menu. The ideas apply to any custom component whose main
interaction is a gesture: drag and drop, drawing on a canvas, a map, a
slider-like widget, keyboard shortcuts.

## The problem

Browserless tests run your Vaadin UI on the server without a browser, so
there is no real DOM and no real mouse or keyboard. For the standard
components, the library has the simulation built in: you find a component
with a locator and click, type or upload:

```java
window.findButton().withText("Save").click();
window.findComboBox(String.class).withLabel("Competition day").selectItem("Päivä #1");
window.findUpload().upload("entries.csv", "text/csv", bytes);
```

Your own component has none of that, and its most important feature is
often exactly the one a server-side test can't perform: a drag-and-drop
gesture is a sequence of browser events, and there is no browser.

There are two ways out:

1. **Simulate the DOM events** (not recommended): dig the inner elements
   out of `getElement().getChildren()` and fire synthetic events on them.
   It's fragile, breaking whenever the layout or the event wiring changes,
   and the test ends up describing the implementation instead of the
   behaviour.
2. **Give the component a Java API for what the user does** (recommended):
   a public method that performs the user's intent on the model. Drag and
   drop, the context menu and the tests all call it, so they all exercise
   the same code path.

## The design rule

Route every way of doing something through one public method:

```
 drop listener ───────────┐
 context menu item ───────┤
 keyboard shortcut ───────┼──►  moveItem(itemId, laneId, index)
 your test ───────────────┘              │
                                          ▼
                              update the model, re-time the lane
                                          │
                                          ▼
                              render() + onChange callback
```

`moveItem` is the single source of truth. The gestures only translate the
browser events into a call to it, and the test calls it directly. The test
then says nothing about how the menu is built or where things are on the
screen, only about what moving an item does.

## Example: `VisualLanePlanner`

The component documents a "Tester API" section in its Javadoc:

```java
public class VisualLanePlanner extends Composite<VVerticalLayout> {

    // --- Tester API ---
    public List<String> laneIds() { ... }
    public String laneOf(String itemId) { ... }
    public LocalTime firstStartOf(String itemId) { ... }
    public String warningOf(String itemId) { ... }

    public void moveItem(String itemId, String targetLaneId, int insertIndex) {
        // update the model, re-time, render, then:
        onChange.accept(plan);
    }

    public void moveItemToLane(String itemId, String targetLaneId) {
        moveItem(itemId, targetLaneId, -1);
    }
}
```

The paths users see are thin wrappers around it:

```java
// Context menu (right-click, or long-press on touch devices)
parent.getSubMenu().addItem(laneId, e -> moveItemToLane(item.id(), laneId));

// Drop listener on each lane: the cursor position becomes an index
addDropListener(event -> event.getDragData().ifPresent(data -> {
    if (data instanceof String itemId) {
        moveItem(itemId, laneId, insertIndexForCursor(event.getOffsetX(), itemId));
    }
}));
```

And the test drives the component through the same API, while using
locators for everything standard around it:

```java
@Test
void draggingASeriesIntoAClashShowsAWarning() {
    window.navigate(GanttView.class);
    VisualLanePlanner planner = window.find(VisualLanePlanner.class).single();

    // The drag-and-drop gesture, as a Java call
    planner.moveItem("H65", "STA1-2", 0);

    assertThat(planner.warningOf("H65")).contains("D45 and H65");
    PlanWarningsPanel warnings = window.find(PlanWarningsPanel.class).single();
    assertThat(window.findListItem().inside(warnings).components()).isNotEmpty();
}
```

Where a feature is built from standard components, test it through them.
The lane edit dialog of the same component is a `Dialog` with a
`TextField` and a `TimePicker`, so its test clicks the real button and
fills in the real fields:

```java
window.findButton().withAriaLabel("Edit lane LANE-2").click();
window.findTextField().withLabel("Lane name").setValue("North gate");
window.findTimePicker().withLabel("First start").setValue(LocalTime.of(11, 30));
window.findButton().withText("Save").click();

assertThat(planner.laneOf("gamma")).isEqualTo("North gate");
```

An accessible name (`setAriaLabel`) on an icon-only button does double
duty: screen readers can announce it, and tests can find the button by it.

## Why this way

- **Stable tests.** The rendering changes easily: the "…" menu inside each
  box of the first version became a context menu, and the item boxes were
  restyled more than once. A method on the model level is a stable
  contract, and none of those changes touched the tests.
- **Readable tests.** The reader doesn't have to know how the menu is
  built. The call states the intent.
- **Fast tests.** No waiting for rendering or polling for elements.
- **New inputs stay covered.** A keyboard shortcut added later calls the
  same method, so the existing tests still cover the logic behind it.
- **A better component.** An API for what the user does is useful outside
  tests too: the application can offer a keyboard or menu alternative to a
  gesture, which is also an accessibility win.

## What this doesn't replace

A tester API doesn't tell whether the user can actually *see* and *use*
the component: CSS clipping, z-index, scrolling, a box hanging over the
edge of its lane. Those are browser questions, and they deserve a few
real-browser tests (Playwright in this project), not more browserless
ones. A browserless test is about behaviour, not pixels.

The gesture itself can be tested in a real browser too: Playwright's
`locator.dragTo(target)` drives Vaadin's HTML5 drag and drop just fine.
One such smoke test is enough to know the wiring works. The rules behind
every drop belong to the fast tests.

## Three levels of tests

The component lives in its own Maven module, and its tests are split into
three levels:

| Level | Where | Run with |
| --- | --- | --- |
| Plain JUnit | `unit/VisualLanePlannerTest.java` | `mvn test` |
| Browserless | `unit/*BrowserlessTest.java` | `mvn test` |
| Playwright (real browser) | `it/*IT.java` (failsafe plugin) | `mvn verify` |

- **Plain JUnit** is the fastest and doesn't need a Vaadin UI at all. It
  tests the model behind the tester API: `moveItemToLane`, `firstStartOf`
  and friends.
- **Browserless** renders the component for real on the server, in a demo
  view. The rendered tree is there to query: item boxes carry a
  `data-item` attribute, so
  `window.findDiv().withAttribute("data-item", "alpha")` finds one, for
  example to check its colour or tooltip. Moves still go through the
  tester API instead of simulated events.
- **Playwright** runs headless Chromium against a Spring Boot test server.
  It checks there are no JavaScript errors (`mopo.assertNoJsErrors()`),
  that the boxes render, and measures the layout: the chart uses the width
  it gets, the time labels don't overlap when the window shrinks, and the
  drop indicator follows the cursor.

Recommendation for new custom components: have all three. Plain JUnit
tells the logic is right, browserless that it renders and works with the
rest of the UI, Playwright that the browser agrees.

## Summary

1. **Don't** dig into a custom component's DOM from a test.
2. **Do** give the component a public API at the level of the user's
   intent.
3. **Route** every input (gesture, menu, keyboard, test) through that same
   method.
4. **Document** the methods under a "Tester API" heading in the
   component's Javadoc, for readers and for future tests.
5. **Test** the standard components inside your component with locators,
   and keep a few real-browser tests for what only a browser can tell.
