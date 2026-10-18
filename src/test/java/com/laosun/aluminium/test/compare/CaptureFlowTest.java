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
 * about that fight: 82 events collapse into <b>13 casts, 4 derived hit groups and 1 break</b>, every event is accounted
 * for, and all four characters act. If the grouping rule ever changes shape, these fail.
 *
 * <p>The rule itself is stated in {@link CaptureFlow}'s class comment: a cast is a run of one attacker's events of one
 * {@code attack_type} ({@code 1} basic / {@code 3} ultimate / {@code 12} memosprite skill), additional damage
 * ({@code attack_type 6}) and breaks ({@code 10}) are <b>not</b> casts, and the {@code True} companions
 * ({@code attack_type 13}) attach first-in-first-out to the hits waiting for one.
 *
 * <p>The 13 are not 21: the earlier rule grouped by {@code raw_damage_before_modifiers}, which split one cast whenever a
 * kit gives its hits different shares (长夜's six instances are {@code 0.1 x 5 + 0.5}, so its single cast looked like
 * "5 + 1") and counted derived hits as casts.
 */
public class CaptureFlowTest {

    private static final String FIXTURE = "src/test/resources/captures/veritas-085418.json";

    /**
     * Every event of the capture must land in exactly one group.
     */
    @Test
    public void everyEventLandsInOneGroup() {
        String out = flow();
        Assertions.assertTrue(out.contains("covered: 82 / 82 events"),
                "the grouping must account for every event of the capture; output was:\n" + out);
        Assertions.assertTrue(out.contains("events:  82 ; casts: 13 ; derived hits: 4 ; breaks: 1"),
                "this fixture is 82 events: 13 casts, 4 derived groups and 1 break; output was:\n" + out);
    }

    /**
     * The shape of the fight: a six-instance single-target cast, area casts, and all four characters acting.
     */
    @Test
    public void theFlowHasTheShapeOfTheFight() {
        String out = flow();
        Assertions.assertTrue(out.lines().anyMatch(line -> line.trim().matches("1\\s+\\d+\\s+\\S+\\s+[\\d.]+\\s+6\\s+1\\s+.*")),
                "cast 1 is 长夜's six-instance single-target skill (five 0.1-split hits + one 0.5-split hit); output was:\n" + out);
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
