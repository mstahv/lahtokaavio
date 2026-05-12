package fi.virit.lahtokaavio.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Shipped demo data (Viking-Line / Pirilä-style sample files) bundled under
 * {@code /demo/} on the classpath. The setup wizard exposes a one-click
 * "Use demo files" shortcut that feeds these through the same upload
 * handlers a real user would trigger.
 */
public final class DemoFiles {

    public static final String COURSES_L1 = "/demo/Viking-Line-L1.xml";
    public static final String COURSES_L2 = "/demo/Viking-Line-L2.xml";
    public static final String COURSES_RRTR = "/demo/Viking-Line-RRTR.xml";
    public static final String REGISTRATIONS = "/demo/ilmoittautumiset.csv";

    private DemoFiles() {
    }

    public static byte[] read(String classpathResource) {
        try (InputStream in = DemoFiles.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing demo resource: " + classpathResource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
