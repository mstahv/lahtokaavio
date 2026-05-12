package in.virit.vlp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot bootstrap used by the demo UIs and Playwright IT.
 * Run from your IDE for quick visual tweaking; the same class is the
 * {@code @SpringBootTest} entry point for {@code *IT} tests.
 */
@SpringBootApplication
public class TestServer {

    public static void main(String... args) {
        SpringApplication.run(TestServer.class, args);
    }
}
