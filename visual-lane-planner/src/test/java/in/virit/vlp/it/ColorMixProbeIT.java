package in.virit.vlp.it;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import in.virit.vlp.LaneItem;
import in.virit.vlp.LanePlan;
import in.virit.vlp.VisualLanePlanner;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Visual probe: render seven items with neighbouring numeric colour keys
 * ("164"…"170") and capture a screenshot, so a human can verify the SplitMix
 * mixer puts their hues at visibly different positions instead of stacking
 * them next to each other on the wheel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ColorMixProbeIT {

    @Value("${local.server.port}")
    private int port;

    private static final Playwright playwright = Playwright.create();
    private Browser browser;
    private Page page;

    @BeforeEach
    void setup() {
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1400, 400));
        page.setDefaultTimeout(60000);
    }

    @AfterEach
    void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
    }

    @Test
    public void neighbouringControlCodesMapToDistantHues() {
        page.navigate("http://localhost:" + port + "/colorprobe");
        page.locator("[data-item]").first().waitFor();

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("/tmp/vlp-color-mix.png"))
                .setFullPage(true));
    }

    @Route("colorprobe")
    public static class ColorProbeUI extends VerticalLayout {

        public ColorProbeUI() {
            Map<String, Integer> offsets = new LinkedHashMap<>();
            offsets.put("ROW", 0);
            VisualLanePlanner planner = new VisualLanePlanner();
            planner.setPlan(new LanePlan(
                    LocalTime.of(11, 0),
                    2,
                    List.of("ROW"),
                    offsets,
                    List.of(
                            new LaneItem("i164", "ROW", "164", 1, 4, 164, null),
                            new LaneItem("i165", "ROW", "165", 1, 4, 165, null),
                            new LaneItem("i166", "ROW", "166", 1, 4, 166, null),
                            new LaneItem("i167", "ROW", "167", 1, 4, 167, null),
                            new LaneItem("i168", "ROW", "168", 1, 4, 168, null),
                            new LaneItem("i169", "ROW", "169", 1, 4, 169, null),
                            new LaneItem("i170", "ROW", "170", 1, 4, 170, null))),
                    p -> Notification.show("changed"));
            add(planner);
        }
    }
}
