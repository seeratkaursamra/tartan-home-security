package tartan.smarthome.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


public final class LogAssertions {
    private LogAssertions() {}

    public static void assertLogContains(StringBuffer log, String needle) {
        String txt = log.toString().toLowerCase();
        assertTrue(
                txt.contains(needle.toLowerCase()),
                "Expected log to contain: " + needle + "\nActual log:\n" + log
        );
    }

    public static void assertLogNotContains(StringBuffer log, String needle) {
        String txt = log.toString().toLowerCase();
        assertFalse(
                txt.contains(needle.toLowerCase()),
                "Expected log NOT to contain: " + needle + "\nActual log:\n" + log
        );
    }
}
