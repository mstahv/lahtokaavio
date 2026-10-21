package in.virit.vlp.it;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Manual visual probe. Captures a screenshot of the basic demo and prints
 * box / track dimensions so we can spot clipping or misalignment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class VlpVisualProbeIT {

    @Value("${local.server.port}")
    private int port;

    private static final Playwright playwright = Playwright.create();

    private Browser browser;
    private Page page;

    @BeforeEach
    public void setup() {
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage(new Browser.NewPageOptions()
                .setViewportSize(1400, 600));
        page.setDefaultTimeout(60000);
    }

    @AfterEach
    public void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
    }

    @Test
    public void screenshotAndMeasure() {
        page.navigate("http://localhost:" + port + "/basic");
        page.locator("[data-item]").first().waitFor();
        page.waitForTimeout(500); // let a resize driven re-render settle

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("/tmp/vlp-basic.png"))
                .setFullPage(true));

        int count = page.locator("[data-item]").count();
        System.out.println("=== VLP probe: " + count + " items ===");
        for (int i = 0; i < count; i++) {
            Locator box = page.locator("[data-item]").nth(i);
            String id = box.getAttribute("data-item");
            var bb = box.boundingBox();
            // The parent is the track Div with data-lane-track.
            Locator track = box.locator("xpath=..");
            var tb = track.boundingBox();
            System.out.printf(
                    "  item=%-6s box: y=%.1f h=%.1f bottom=%.1f  track: y=%.1f h=%.1f bottom=%.1f  laneFits=%s%n",
                    id, bb.y, bb.height, bb.y + bb.height,
                    tb.y, tb.height, tb.y + tb.height,
                    (bb.y >= tb.y && bb.y + bb.height <= tb.y + tb.height));
        }
    }
}
