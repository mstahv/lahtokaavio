package in.virit.vlp.it;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import in.virit.mopo.Mopo;
import in.virit.vlp.BasicVisualLanePlannerUsageUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real-browser sanity test: boots the demo UI, opens it in headless Chromium,
 * asserts no JS errors and that the expected item boxes render. We
 * deliberately don't simulate the actual HTML5 drag-and-drop event sequence
 * here (it's fragile and not the value-add of this test) — the model-level
 * tester API in {@code VisualLanePlannerBrowserlessTest} covers that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class VisualLanePlannerIT {

    @Value("${local.server.port}")
    private int port;

    private static final Playwright playwright = Playwright.create();

    private Browser browser;
    private Page page;
    private Mopo mopo;

    @BeforeEach
    public void setup() {
        browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        // Dev-mode first-boot needs to build the frontend bundle; give it room.
        page.setDefaultTimeout(60000);
        mopo = new Mopo(page);
    }

    @AfterEach
    public void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
    }

    @Test
    public void basicDemoRendersAllItemBoxesWithoutJsErrors() {
        // The demo UI uses @Route("basic"); use the explicit path.
        String url = "http://localhost:" + port + "/basic";
        page.navigate(url);

        Locator itemBoxes = page.locator("[data-item]");
        itemBoxes.first().waitFor();
        assertThat(itemBoxes).hasCount(4);
        assertThat(page.locator("[data-item='alpha']")).isVisible();
        assertThat(page.locator("[data-item='delta']")).isVisible();

        mopo.assertNoJsErrors();
    }
}
