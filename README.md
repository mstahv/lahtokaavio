# Lähtökaavio

A start time planner for orienteering competitions, built with
[Vaadin](https://vaadin.com) and Spring Boot. "Lähtökaavio" is Finnish for
a start schedule.

**Try it: [Vaadin sponsored deployment](https://lahtokaavio.dokku1.parttio.org/)**
("Use demo files" loads a sample competition with one click).

> This app was built mainly as a research vehicle for
> [browserless-test](https://vaadin.com/docs/latest/building-apps/testing/browserless), Vaadin's
> library for testing UIs without a browser: a real application with real
> data and the hard parts (drag and drop, file uploads and downloads, custom
> components) to find out what testing it takes. It works, but it is not a
> maintained product. See
> [docs/testing-custom-components.md](docs/testing-custom-components.md)
> for what came out of it.

## What you can do with it

In an orienteering competition, every class starts its runners one at a
time, usually at a 2 minute interval, from one of the start places. Runners
from the same start to the same first control should not leave together,
and a start can only handle so many runners a minute. Lähtökaavio plans the
classes into parallel start queues ("lanes") so that the competition is as
short as these rules allow, and lets you fine tune the result by hand.

- **Load the courses and the entries.** Courses come as IOF XML (versions
  2 and 3, one or more files, e.g. from OCAD or Purple Pen). The entries
  come from [IRMA](https://irma.suunnistusliitto.fi), either the summary by
  class or the full list of entries, in which case the counts are computed
  for you.
- **Generate a plan.** Set the first start, the start interval, the gap
  between classes in a queue and the maximum starters a minute per start,
  and the planner packs the classes into queues. Open classes ("avoin")
  start after the others, and an open class without a course of its own
  borrows the course of its age class.
- **Edit it as a Gantt chart.** Drag a class to another queue or position,
  or use its context menu. Rename a queue and set its start by hand. The
  plan is checked after every change: classes heading to the same first
  control too close to each other, or too many starters a minute, show up
  as warnings until fixed.
- **Update the counts.** Late entries? Apply the new counts to the queues
  as they are (the competition gets as long as it gets), or re-optimise
  from scratch.
- **Draw the start times.** Load the list of entries and draw the start
  order within each class.
- **Download the results.** The start plan and the start list as CSV,
  optionally in the formats [Navisport](https://navisport.com) imports: the
  start list for its participant import, or the start scheme so that
  Navisport does the draw itself. A JSON backup lets you resume the session
  later.

## Running it locally

You need Java 25 and Maven.

```bash
mvn install -DskipTests
cd app
mvn spring-boot:run
```

Then open <http://localhost:8080>, and click "Use demo files" or load your
own. Set the `PORT` environment variable to use another port.

`mvn install` without `-DskipTests` runs all the tests, including the
real-browser ones (Playwright downloads Chromium on the first run).

## Project structure

| Module | What it is |
| --- | --- |
| [`app`](app) | The Lähtökaavio application (Spring Boot + Vaadin) |
| [`visual-lane-planner`](visual-lane-planner) | The Gantt chart as a reusable, domain-agnostic Vaadin add-on |
| [`docs`](docs) | [Testing custom Vaadin components in browserless tests](docs/testing-custom-components.md) |

The tests come in three levels: plain JUnit for the planning logic,
browserless tests for the UI (most of them, e.g.
[`MetsasprinttiDataTest`](app/src/test/java/fi/virit/lahtokaavio/ui/MetsasprinttiDataTest.java)
runs whole user stories with the files of a real competition), and a few
Playwright tests for what only a browser can tell.

## License

[Apache License 2.0](LICENSE)
