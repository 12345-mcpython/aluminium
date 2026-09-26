package com.laosun.aluminium.test;

import com.laosun.Main;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The two demos in {@link Main} are deliverables — a demo that throws is a broken deliverable, and nothing
 * else in the suite touches them.
 *
 * <p><b>What this pins, and what it deliberately does not.</b> Only "it still runs": the engine changes
 * that would break a demo (a removed method, a changed signature, a state machine that now refuses) surface
 * here instead of the first time somebody runs {@code gradlew run}. It does <b>not</b> pin any number the
 * demos print — those are the unit tests' job ({@code ControlTest}, {@code DotTest},
 * {@code EnemyCampSummonTest}, …), and re-asserting them by scraping stdout would be a second, weaker copy
 * of the same claims.
 *
 * <p>The demos write a lot to stdout; that is left alone (the suite already prints), because redirecting
 * {@code System.out} here would add global state to a smoke test for no gain.
 */
public class DemoSmokeTest {

    @Test
    public void theBattleDemoStillRuns() {
        Assertions.assertDoesNotThrow(() -> Main.main(new String[]{"battle"}),
                "the whole-fight demo must keep running; gradlew run with no argument uses it");
    }

    @Test
    public void theMechanicsDemoStillRuns() {
        Assertions.assertDoesNotThrow(() -> Main.main(new String[]{"mechanics"}),
                "the mechanics demo drives the newest engine parts (control states, a DOT on a character, "
                        + "an enemy-side summon), so it is the first thing to break when one of them changes");
    }

    @Test
    public void noArgumentRunsTheBattleDemo() {
        Assertions.assertDoesNotThrow(() -> Main.main(new String[0]),
                "the default path is what gradlew run exercises, so it must stay valid");
    }

    @Test
    public void anUnknownArgumentIsReportedRatherThanThrown() {
        Assertions.assertDoesNotThrow(() -> Main.main(new String[]{"nonsense"}),
                "a typo in --args should print the choices, not crash");
    }
}
