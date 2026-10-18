package com.laosun.aluminium.test.compare;

import com.laosun.compare.CaptureFlow;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The flow reconstruction, on the committed capture fixture.
 *
 * <h2>What is actually pinned here</h2>
 * Not numbers the engine derives - the capture's own grouping. The fixture is a real fight, so these counts are facts
 * about that fight: 82 events collapse into 21 casts, every event is accounted for, and all four characters act. If the
 * grouping rule ever changes shape, these fail.
 *
 * <p>The rule itself is stated in {@link CaptureFlow}'s class comment: a hit is followed by its {@code True} companion,
 * and one cast repeats the same {@code raw_damage_before_modifiers}.
 */
public class CaptureFlowTest {

    private static final String FIXTURE = "src/test/resources/captures/veritas-085418.json";

    /**
     * Every event of the capture must land in exactly one cast.
     */
    @Test
    public void everyEventLandsInACast() {
        String out = flow();
        Assertions.assertTrue(out.contains("covered: 82 / 82 events"),
                "the grouping must account for every event of the capture; output was:\n" + out);
        Assertions.assertTrue(out.contains("events:  82 ; casts: 21"),
                "this fixture is 82 events in 21 casts; output was:\n" + out);
    }

    /**
     * The shape of the fight: a five-instance single-target cast, area casts, and all four characters acting.
     */
    @Test
    public void theFlowHasTheShapeOfTheFight() {
        String out = flow();
        Assertions.assertTrue(out.lines().anyMatch(line -> line.trim().matches("1\\s+\\d+\\s+\\S+\\s+[\\d.]+\\s+5\\s+1\\s+.*")),
                "cast 1 is a five-instance single-target hit; output was:\n" + out);
        Assertions.assertTrue(out.lines().anyMatch(line -> line.trim().matches("\\d+\\s+\\d+\\s+\\S+\\s+[\\d.]+\\s+2\\s+2\\s+.*")),
                "the fight has area casts (2 targets); output was:\n" + out);
        for (String actor : new String[]{"1413", "1409", "1415", "1512"}) {
            java.util.regex.Pattern line = java.util.regex.Pattern.compile("(?m)^\\s*" + actor + "\\s+casts:");
            Assertions.assertTrue(line.matcher(out).find(),
                    "actor " + actor + " must appear in the per-actor summary; output was:\n" + out);
        }
    }

    /**
     * Runs the tool and returns what it printed.
     */
    private static String flow() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            int exit = CaptureFlow.run("capture-flow", FIXTURE);
            Assertions.assertEquals(0, exit, "capture-flow must succeed on the fixture");
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
