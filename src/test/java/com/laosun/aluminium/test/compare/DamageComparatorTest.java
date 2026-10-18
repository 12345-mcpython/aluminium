package com.laosun.aluminium.test.compare;


import com.laosun.compare.DamageComparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The comparison tool itself: a shipped scenario must run green, a wrong number must fail, and a misspelled zone must be
 * reported as an error rather than silently skipped.
 *
 * <p>Why the tool needs a judge at all: it is the thing that decides whether the engine reproduces the game, so a
 * comparison that quietly compared <b>nothing</b> (an empty {@code expect}, a typo in a zone name, a scenario that throws)
 * would look exactly like a pass. The three cases below are those three ways.
 */
public class DamageComparatorTest {

    /**
     * The shipped skeleton scenario: its {@code expect} currently carries the engine's own measured numbers, so it must
     * match. When the game's numbers replace them, this case keeps checking that the file still <b>runs</b> (and will fail
     * only if the engine moves away from what the file states).
     */
    @Test
    public void theShippedScenarioRunsAndMatches() {
        Assertions.assertEquals(0, DamageComparator.run("compare", "scenarios/1002-skill.json"),
                "the shipped scenario must compare cleanly");
    }

    /**
     * The two real game captures: both must reproduce, zone by zone.
     *
     * <p>They are worth keeping side by side because they exercise different halves of the chain:
     * <ul>
     *   <li>{@code capture-858483} - {@code ByAttack}, a boss that was <b>broken</b> (so its whole reduction is the
     *       monster's own 0.75), no element boost, and the DEF supplied by reverse-engineering;</li>
     *   <li>{@code capture-876170} - {@code ByMaxHP} with {@code damage_percentage 3.6}, a target that was <b>not</b>
     *       broken (so its 0.1 reduction IS the toughness 10%), the element boost split from the all-type boost, and
     *       <b>DEF given by the game</b> (1150) - the one capture where the defence zone is checked against a number
     *       the game states rather than one derived from the total.</li>
     * </ul>
     */
    @Test
    public void allRealCapturesReproduce() {
        Assertions.assertEquals(0, DamageComparator.run("compare", "scenarios/capture-858483.json"),
                "the ByAttack capture must compare cleanly");
        Assertions.assertEquals(0, DamageComparator.run("compare", "scenarios/capture-876170.json"),
                "the ByMaxHP capture must compare cleanly");
        // #12 of the whole-battle capture: the one event whose implied DEF is exactly the stated 1150 (no DEF debuff),
        // so it is replayable without knowing the debuff state - see the audit in scenarios/README.md.
        Assertions.assertEquals(0, DamageComparator.run("compare", "scenarios/capture-174678.json"),
                "the whole-battle event must compare cleanly");
    }

    /**
     * A scenario that states a wrong number must fail - otherwise a typo in {@code expect} reads as a pass.
     */
    @Test
    public void aWrongNumberFails(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("wrong.json");
        Files.writeString(file, scenarioWith("{\"final\": 1.0}"), StandardCharsets.UTF_8);
        Assertions.assertEquals(1, DamageComparator.run("compare", file.toString()),
                "a final that is not the engine's must be a mismatch");
    }

    /**
     * A zone the engine does not have must be an <b>error</b>, not "not compared": a misspelled zone would otherwise make
     * a scenario look complete while comparing one row fewer than the author thinks.
     */
    @Test
    public void aMisspelledZoneFails(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("typo.json");
        Files.writeString(file, scenarioWith("{\"BoostArea\": 1.224, \"FinalBoost\": 1.0}"), StandardCharsets.UTF_8);
        Assertions.assertEquals(1, DamageComparator.run("compare", file.toString()),
                "FinalBoost is not a zone; the run must report it instead of ignoring it");
    }

    /**
     * A scenario with no expectations at all is a <b>listing</b>, not a comparison - and it says so (exit 0).
     */
    @Test
    public void anEmptyExpectationListIsAListingNotAPass(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.json");
        Files.writeString(file, scenarioWith("{}"), StandardCharsets.UTF_8);
        Assertions.assertEquals(0, DamageComparator.run("compare", file.toString()),
                "without expectations the tool prints the engine's numbers; that is not a failure");
    }

    /** The smallest scenario that runs: 1002's Skill at Lv10 against the fixture monster, not critting. */
    private static String scenarioWith(String expect) {
        return """
                {
                  "name": "test",
                  "attacker": { "cid": 1002, "level": 80, "eidolon": 0, "skill": "SKILL", "skillLevel": 10 },
                  "target": { "monsterId": 1002011, "level": 90 },
                  "crit": false,
                  "instance": 0,
                  "instances": 4,
                  "expect": %s
                }
                """.formatted(expect);
    }
}
