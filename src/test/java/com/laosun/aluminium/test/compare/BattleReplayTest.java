package com.laosun.aluminium.test.compare;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.compare.BattleReplay;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The whole-battle replay on the committed fixture: <b>what it is allowed to claim</b>, and what it is not.
 *
 * <h2>What is pinned here</h2>
 * <ul>
 *   <li>the plan covers the capture: every captured cast has an entry, and every entry names the actor and the
 *       target the payload names (the manual cast → slot table's guard against drift);</li>
 *   <li>the enemies the driver assembles are the ones the capture states - 生命上限 and 防御 compared against the
 *       payload, not against the plan (so a plan that quietly invents a number fails);</li>
 *   <li>the casts really reach the engine's own machinery: 长夜's six-instance row settles six instances, 1409's
 *       tally rule reaches every living enemy, and 1415's four one-hit casts settle twelve instances;</li>
 *   <li>the one cast the data cannot drive is <b>reported</b>, not skipped.</li>
 * </ul>
 *
 * <p><b>What is deliberately NOT pinned: the settled totals.</b> The capture is a window of a fight already in
 * progress, so the states standing before event 0 are not in the payload; the reconciliation is the tool's output
 * and its open causes are registered in {@code AGENT.md} §3.7zb. Pinning today's totals would pin the gap itself.
 */
public class BattleReplayTest {

    private static final String PLAN = "scenarios/veritas-085418.replay.json";
    private static final String CAPTURE = "src/test/resources/captures/veritas-085418.json";

    /**
     * Every captured cast is driven, except the one whose data row does not exist - and that one is named.
     */
    @Test
    public void thePlanDrivesEveryCastTheDataCanDrive() {
        Run run = run(PLAN);
        Assertions.assertEquals(0, run.exit, "the replay must complete on the committed plan; output was:\n" + run.out);
        Assertions.assertTrue(run.out.contains("casts: 13 in the capture ; driven: 12 ; not drivable: 1"),
                "13 captured casts, 12 driven: 长夜's 强化普攻 (slot 8) has no row in skills.json; output was:\n" + run.out);
        Assertions.assertTrue(run.out.contains("the memosprite has no skill at data slot 8"),
                "the cast that cannot be driven must say WHY, not vanish; output was:\n" + run.out);
    }

    /**
     * The enemies are the capture's own numbers. Compared against the payload, so the plan cannot drift from it.
     */
    @Test
    public void theEnemiesAreTheOnesTheCaptureStates() {
        Run run = run(PLAN);
        JsonObject capture = JsonParser.parseString(read(CAPTURE)).getAsJsonObject();
        Set<Double> lives = new LinkedHashSet<>();
        Set<Double> defences = new LinkedHashSet<>();
        var events = capture.getAsJsonArray("damage_events");
        for (int i = 0; i < events.size(); i++) {
            JsonObject zones = events.get(i).getAsJsonObject().getAsJsonObject("zones");
            lives.add(zones.get("target_max_hp").getAsDouble());
            defences.add(zones.get("target_defence").getAsDouble());
        }
        Assertions.assertEquals(3, lives.size(), "precondition: the capture states three distinct targets");
        for (double life : lives) {
            Assertions.assertTrue(run.out.contains(String.format(java.util.Locale.ROOT, "HP %.6f", life)),
                    "the assembled enemy with 生命上限 " + life + " must be on the field; output was:\n" + run.out);
        }
        for (double defence : defences) {
            Assertions.assertTrue(run.out.contains(String.format(java.util.Locale.ROOT, "DEF %.6f", defence)),
                    "and its 防御 must be the capture's " + defence + "; output was:\n" + run.out);
        }
    }

    /**
     * The casts reach the engine's own paths: a segmented row settles per segment, a rules-delivered cast reaches
     * every living enemy, and a repeated cast settles once per repeat.
     */
    @Test
    public void theCastsReachTheEnginesOwnMachinery() {
        String out = run(PLAN).out;
        assertRow(out, 1, 6, "长夜's 忆灵技1 is a six-instance row (skill_effects.json 11413/1 segments)");
        assertRow(out, 2, 3, "「小伊卡对敌方全体造成…」 is delivered by the rule table, so it reaches all three "
                + "living enemies (the capture shows two because one was already down)");
        assertRow(out, 16, 12, "1415's 德谬歌 casts a ONE-hit skill four times in a row (the capture groups them as "
                + "one cast), against three living enemies");
    }

    /**
     * The structural guard: a plan that names a different target than the capture does is refused, not replayed.
     */
    @Test
    public void aPlanThatDisagreesWithTheCaptureIsRefused() {
        JsonObject plan = JsonParser.parseString(read(PLAN)).getAsJsonObject();
        plan.getAsJsonArray("casts").get(0).getAsJsonObject().addProperty("target", 2);
        Path temp = Assertions.assertDoesNotThrow(() -> Files.createTempFile("replay-disagreeing-", ".json"),
                "a temp file for the mutated plan");
        try {
            Assertions.assertDoesNotThrow(() -> Files.writeString(temp,
                    new GsonBuilder().setPrettyPrinting().create().toJson(plan), StandardCharsets.UTF_8),
                    "writing the mutated plan");
            Run run = run(temp.toString());
            Assertions.assertEquals(2, run.exit, "a plan whose target is not the capture's must be refused; output "
                    + "was:\n" + run.out);
            Assertions.assertTrue(run.out.contains("was aimed at enemy 1 in the capture but at 2 in the plan"),
                    "and it must say which cast and which two targets; output was:\n" + run.out);
        } finally {
            Assertions.assertDoesNotThrow(() -> Files.deleteIfExists(temp), "temp plan cleanup");
        }
    }

    /**
     * The row of one cast: its settled instance count, as the report printed it.
     */
    private static void assertRow(String out, int cast, int instances, String why) {
        String prefix = String.format(java.util.Locale.ROOT, "%-4d ", cast);
        for (String line : out.split("\\R")) {
            if (line.startsWith(prefix) && line.contains("h/")) {
                String[] cells = line.substring(prefix.length()).trim().split("\\s+");
                Assertions.assertEquals(String.valueOf(instances), cells[cells.length - 3],
                        "cast " + cast + ": " + why + "; the row was:\n" + line);
                return;
            }
        }
        Assertions.fail("cast " + cast + " has no row in the report:\n" + out);
    }

    /** What one run printed, and its exit code. */
    private record Run(int exit, String out) {
    }

    /**
     * Runs the tool with {@code System.out} captured, the way the other diagnostics' judges do.
     */
    private static Run run(String plan) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exit;
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            exit = BattleReplay.run("replay", plan);
        } finally {
            System.setOut(original);
        }
        return new Run(exit, buffer.toString(StandardCharsets.UTF_8));
    }

    private static String read(String path) {
        return Assertions.assertDoesNotThrow(
                () -> Files.readString(Path.of(path), StandardCharsets.UTF_8),
                "reading " + path);
    }
}
