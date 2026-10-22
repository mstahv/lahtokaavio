package in.virit.vlp.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The planner should use the width it gets: the whole chart fits, and the
 * lane tracks cover every item box (nothing hangs over the track's edge).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WidePlanProbeIT {

    @Value("${local.server.port}")
    private int port;

    private static final Playwright playwright = Playwright.create();

    private Browser browser;
    private Page page;

    @BeforeEach
    public void setup() {
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1400, 700));
        page.setDefaultTimeout(60000);
    }

    @AfterEach
    public void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
    }

    @Test
    public void chartUsesTheAvailableWidth() {
        page.navigate("http://localhost:" + port + "/wide");
        page.locator("[data-item]").first().waitFor();
        page.waitForTimeout(500); // let a resize driven re-render settle
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/vlp-wide.png")).setFullPage(true));

        BoundingBox planner = page.locator("[data-component=visual-lane-planner]").boundingBox();
        double rightmostItem = 0;
        int count = page.locator("[data-item]").count();
        for (int i = 0; i < count; i++) {
            BoundingBox box = page.locator("[data-item]").nth(i).boundingBox();
            rightmostItem = Math.max(rightmostItem, box.x + box.width);
        }
        double trackRight = 0;
        int tracks = page.locator("[data-lane-track]").count();
        for (int i = 0; i < tracks; i++) {
            BoundingBox t = page.locator("[data-lane-track]").nth(i).boundingBox();
            trackRight = Math.max(trackRight, t.x + t.width);
        }
        System.out.printf("planner x=%.0f w=%.0f right=%.0f | rightmost item=%.0f | track right=%.0f%n",
                planner.x, planner.width, planner.x + planner.width, rightmostItem, trackRight);

        assertThat(rightmostItem).as("all items visible in the planner")
                .isLessThanOrEqualTo(planner.x + planner.width + 1);
        assertThat(trackRight).as("tracks cover the items").isGreaterThanOrEqualTo(rightmostItem - 1);
        assertThat(rightmostItem).as("most of the width in use")
                .isGreaterThan(planner.x + planner.width * 0.8);
    }

    @Test
    public void chartRescalesWhenTheWindowShrinks() {
        page.navigate("http://localhost:" + port + "/wide");
        page.locator("[data-item]").first().waitFor();
        page.waitForTimeout(500);

        page.setViewportSize(800, 700);
        page.waitForTimeout(500);
        BoundingBox planner = page.locator("[data-component=visual-lane-planner]").boundingBox();
        assertThat(rightmostItem()).as("still fits after shrinking")
                .isLessThanOrEqualTo(planner.x + planner.width + 1);
        assertTimeLabelsDoNotOverlap();

        // A few px per minute: 5-minute ticks would pile up
        page.setViewportSize(450, 700);
        page.waitForTimeout(500);
        assertTimeLabelsDoNotOverlap();

        // Too narrow even for the smallest scale: scroll, don't clip
        page.setViewportSize(250, 700);
        page.waitForTimeout(500);
        Object scrollable = page.locator("[data-component=visual-lane-planner]")
                .evaluate("el => el.scrollWidth > el.clientWidth");
        assertThat(scrollable).isEqualTo(true);
    }

    /** On a smaller scale the axis shows fewer ticks, not a pile of labels. */
    private void assertTimeLabelsDoNotOverlap() {
        Object overlaps = page.locator("[data-component=visual-lane-planner]").evaluate("""
                el => {
                  const labels = Array.from(el.querySelectorAll('[data-time-tick]'))
                      .map(t => t.getBoundingClientRect());
                  return labels.some((r, i) => i > 0 && r.left < labels[i - 1].right);
                }""");
        assertThat(page.locator("[data-time-tick]").count()).isGreaterThan(1);
        assertThat(overlaps).as("time labels overlap").isEqualTo(false);
    }

    private double rightmostItem() {
        double right = 0;
        int count = page.locator("[data-item]").count();
        for (int i = 0; i < count; i++) {
            BoundingBox box = page.locator("[data-item]").nth(i).boundingBox();
            right = Math.max(right, box.x + box.width);
        }
        return right;
    }
}
