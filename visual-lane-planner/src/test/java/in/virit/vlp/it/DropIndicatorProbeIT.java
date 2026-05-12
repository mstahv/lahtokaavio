package in.virit.vlp.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that a synthetic {@code dragover} on a lane track shows the drop
 * indicator at a position derived from the cursor's x coordinate — the
 * "you would drop here" feedback the user can see while dragging.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DropIndicatorProbeIT {

    @Value("${local.server.port}")
    private int port;

    private static final Playwright playwright = Playwright.create();

    private Browser browser;
    private Page page;

    @BeforeEach
    public void setup() {
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1400, 600));
        page.setDefaultTimeout(60000);
    }

    @AfterEach
    public void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
    }

    @Test
    public void dragoverPositionsIndicatorOnTrack() {
        page.navigate("http://localhost:" + port + "/basic");
        page.locator("[data-item]").first().waitFor();

        // Drop indicator should be hidden initially.
        Object before = page.evaluate("""
                () => document.querySelector("[data-lane-track='LANE-2'] [data-drop-indicator]").style.display
                """);
        assertThat(before.toString()).isEqualTo("none");

        // Fire a dragover at x = 50px into LANE-2's track.
        Object result = page.evaluate("""
                () => {
                  const track = document.querySelector("[data-lane-track='LANE-2']");
                  const rect = track.getBoundingClientRect();
                  track.dispatchEvent(new DragEvent('dragover', {
                    bubbles: true,
                    cancelable: true,
                    clientX: rect.left + 50,
                    clientY: rect.top + 10,
                    dataTransfer: new DataTransfer()
                  }));
                  const ind = track.querySelector('[data-drop-indicator]');
                  return ind.style.display + '|' + ind.style.left;
                }
                """);
        assertThat(result.toString()).startsWith("block|");

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("/tmp/vlp-drop-indicator.png"))
                .setFullPage(true));

        // dragleave on the track (not on a child) clears the indicator.
        Object after = page.evaluate("""
                () => {
                  const track = document.querySelector("[data-lane-track='LANE-2']");
                  const ev = new DragEvent('dragleave', {bubbles: true, cancelable: true});
                  Object.defineProperty(ev, 'target', {value: track});
                  track.dispatchEvent(ev);
                  return track.querySelector('[data-drop-indicator]').style.display;
                }
                """);
        assertThat(after.toString()).isEqualTo("none");
    }
}
