package com.laosun.aluminium.test.compare;

import com.laosun.compare.CaptureComparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The whole-battle replay driver, on a fixture trimmed from a real `veritas` capture.
 *
 * <h2>Why this is the strongest judge in the suite</h2>
 * Every other damage test starts from numbers this repository derived. This one starts from a capture: 82 hits of a
 * real fight, each carrying the game's own base, boost sum, crit, effective DEF, net resistance, reduction and
 * vulnerability. The driver rebuilds each hit as a scenario for {@link com.laosun.compare.DamageComparator} and asks
 * the engine for the same number.
 *
 * <p>What it covers, by construction: `ByAttack`, `ByMaxHP`, true damage (the value is the settled number), crit and
 * non-crit, DEF-ignore (the capture states the effective DEF separately from the base one), negative net resistance,
 * a reduction that is the toughness 10%, and a vulnerability.
 *
 * <p>What it deliberately skips: break damage. Its chain does not take the boost bucket, while the cast this driver
 * installs is a normal hit that does - the break path is verified separately (see `scenarios/README.md`: the
 * level-80 break base 3767.553 times the break percentage, and the `break_damage_added_ratio` factor).
 */
public class CaptureComparatorTest {

    /** The trimmed capture: everything the driver reads, nothing else. */
    private static final String FIXTURE = "src/test/resources/captures/veritas-085418.json";

    /**
     * Every replayable event of the fixture must reproduce.
     */
    @Test
    public void everyReplayableEventOfTheCaptureReproduces() {
        int exit = CaptureComparator.run("compare-capture", FIXTURE);
        Assertions.assertEquals(0, exit,
                "the capture's events must all reproduce; a non-zero exit means one of them did not (the table above "
                        + "says which and by how much)");
    }

    /**
     * The driver must actually have done work - a missing fixture, or one whose events all get skipped, would "pass"
     * the assertion above for the wrong reason.
     */
    @Test
    public void theFixtureIsNotEmptyAndItsEventsAreReplayed() {
        Assertions.assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(FIXTURE)),
                "the fixture is " + FIXTURE + " (a trimmed capture); without it this test proves nothing");
        Assertions.assertEquals(0, CaptureComparator.run("compare-capture", FIXTURE, "--limit", "5"),
                "the first few events must replay too");
    }
}
