package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.battle.AutoPolicy;
import com.laosun.aluminium.battle.BattleDriver;
import com.laosun.aluminium.battle.BattleListener;
import com.laosun.aluminium.battle.SkillPolicy;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A whole battle runs to a decision with no human, and says how it ended.
 *
 * <p>The point of these cases is not that a particular unit wins. It is that a battle now has a
 * <em>conclusion it can be asked about</em>: before {@code BattleDriver} existed the turn cycle was
 * written inline in the demo, so "play until somebody wins" could only be observed by reading printed
 * output. A test could not assert it, and a stall could not be told apart from a long fight.
 *
 * <p>Determinism matters for the same reason: the same seed must reach the same decision, or a
 * failure here would not be reproducible.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the six scenarios below, one
 * {@code assertAll} per scenario so a failing clause still names itself. The turn-limit cases whose
 * fixtures differ - an idle hero, a refusing policy - stay their own scenarios instead of sharing an
 * {@code assertAll}.
 */
public class BattleDriverTest {

    /**
     * A seeded battle against a killable enemy is decided; the same seed decides it the same way, and a
     * listener may watch it without changing the outcome.
     *
     * <p>One scenario because every clause stands on this class's seeded fixture (a real character
     * against one killable dummy) and reads the same object, the {@code BattleDriver.Result} that run
     * produces: the decision itself, its reproduction from the same seed, and the fact that observing
     * the run - the event order, the start/end pairing, and the silent-versus-watched outcome - leaves
     * it untouched.
     */
    @Test
    void aSeededBattleIsDecidedReproduciblyAndListenersDoNotChangeIt() {
        BattleDriver.Result first = seededBattle().run();
        BattleDriver.Result second = seededBattle().run();

        BattleDriver.Result silent = seededBattle().run();
        BattleDriver.Result watched = seededBattle()
                .withListener(new BattleListener() {
                    @Override
                    public void onTurnStart(CanHit actor, int turn) {
                        // a listener that reads state must not perturb the random stream
                        actor.getName();
                    }
                })
                .run();

        List<String> events = new ArrayList<>();
        BattleDriver.Result observed = seededBattle()
                .withListener(new BattleListener() {
                    @Override
                    public void onBattleStart(BattleDriver d) {
                        events.add("start");
                    }

                    @Override
                    public void onTurnStart(CanHit actor, int turn) {
                        events.add("turn+" + turn + ":" + actor.getName());
                    }

                    @Override
                    public void onAction(CanHit actor, SkillPolicy.Decision decision) {
                        events.add("act:" + actor.getName());
                    }

                    @Override
                    public void onTurnEnd(CanHit actor, int turn) {
                        events.add("end+" + turn + ":" + actor.getName());
                    }

                    @Override
                    public void onBattleEnd(BattleDriver.Result result) {
                        events.add("over:" + result.outcome());
                    }
                })
                .run();

        int starts = 0;
        int ends = 0;
        for (String event : events) {
            if (event.startsWith("turn+")) {
                starts++;
            }
            if (event.startsWith("end+")) {
                ends++;
            }
        }
        final int startsSeen = starts;
        final int endsSeen = ends;

        Assertions.assertAll(
                () -> Assertions.assertTrue(first.isDecided(),
                        "the battle should reach a terminal state, got " + first.outcome()
                                + " after " + first.turns() + " turns"),
                () -> Assertions.assertEquals(Battle.Status.WIN, first.status(),
                        "a lone hero against one weak enemy should win"),
                () -> Assertions.assertTrue(first.turns() > 0,
                        "at least one turn must have been taken"),
                () -> Assertions.assertEquals(first.status(), second.status(),
                        "status must be reproducible"),
                () -> Assertions.assertEquals(first.turns(), second.turns(),
                        "turn count must be reproducible"),
                () -> Assertions.assertEquals("start", events.getFirst(),
                        "the battle start comes first"),
                () -> Assertions.assertTrue(events.getLast().startsWith("over:"),
                        "the battle end comes last, got " + events.getLast()),
                () -> Assertions.assertEquals(observed.turns(), startsSeen,
                        "one start per turn taken"),
                () -> Assertions.assertEquals(startsSeen, endsSeen,
                        "every start must be paired with an end"),
                () -> Assertions.assertEquals(silent.status(), watched.status(),
                        "a listener that only reads must not change the status"),
                () -> Assertions.assertEquals(silent.turns(), watched.turns(),
                        "narration must not consume the battle's randomness"));
    }

    /**
     * The driver must not spin forever when neither side can finish the other.
     *
     * <p>This is the case a bare "while (!isOver())" loop cannot survive, and the reason the turn
     * ceiling exists: a unit that cannot die is not a win, it is a hang. The result has to say so
     * rather than pretend the battle ended.
     */
    @Test
    void anUnwinnableBattleStopsAtTheTurnLimitInsteadOfHanging() {
        // A hero with no skills at all cannot damage anything; the enemy is alive but the hero is the
        // only unit that will ever act as far as this policy is concerned.
        Character hero = Character.fromAttributes("无技者", 100000, 0, 100, 100);
        Enemy foe = Enemy.fromAttributes("不还手的墙", 100000, 0, 100, 100);

        BattleDriver driver = new BattleDriver(BattleFixture.battle(List.of(hero), List.of(foe)),
                new AutoPolicy(), 12);
        BattleDriver.Result result = driver.run();

        Assertions.assertAll(
                () -> Assertions.assertEquals(BattleDriver.Outcome.TURN_LIMIT, result.outcome(),
                        "an undecidable battle must be reported as a turn limit, not as finished"),
                () -> Assertions.assertFalse(result.isDecided(), "a turn limit is not a decision"),
                () -> Assertions.assertEquals(12, result.turns(),
                        "the ceiling should be honoured exactly"));
    }

    /**
     * A single step plays exactly one turn and leaves the battle usable.
     *
     * <p>Stepping is what an interactive client needs, and it must not be a different code path from
     * running: a step that left the action bar inconsistent would corrupt every later turn.
     */
    @Test
    void oneStepPlaysExactlyOneTurnAndLeavesTheBattleConsistent() {
        Battle battle = BattleFixture.battle(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 4000, 0, 100, 100)));
        BattleDriver driver = new BattleDriver(battle, new AutoPolicy());

        battle.startBattle();

        Assertions.assertAll(
                () -> Assertions.assertTrue(driver.step(), "the first step should find an actor"),
                () -> Assertions.assertFalse(battle.isOver(),
                        "one turn against a healthy enemy must not decide the battle"),
                () -> Assertions.assertTrue(driver.step(), "the second step should also find an actor"),
                () -> Assertions.assertFalse(battle.isOver(), "still undecided after two turns"));
    }

    /**
     * When the policy declines, the turn still ends -- the action bar must not stall on a refusal.
     *
     * <p>A policy that returns {@code null} is saying "I have nothing to do", not "freeze the battle".
     * Skipping {@code afterMove} would leave the queue unadvanced and the next step would hand the same
     * unit the same turn forever.
     */
    @Test
    void aPolicyThatAlwaysDeclinesDoesNotStallTheActionBar() {
        Battle battle = BattleFixture.battle(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 5000, 0, 100, 100)));
        SkillPolicy nothing = (b, actor) -> null;
        BattleDriver driver = new BattleDriver(battle, nothing, 8);

        BattleDriver.Result result = driver.run();

        Assertions.assertAll(
                () -> Assertions.assertEquals(BattleDriver.Outcome.TURN_LIMIT, result.outcome(),
                        "a refusal-only policy must still be reported as a turn limit"),
                () -> Assertions.assertEquals(8, result.turns(),
                        "every step must have advanced the bar even though no action was taken"));
    }

    /**
     * The deterministic targeting mode consumes no randomness, proved against an unstepped twin.
     *
     * <p>This is what lets a scripted run stay reproducible. Aggro-weighted selection draws from the
     * battle's random source, so an ally switching to it would shift every later crit roll and a
     * recorded run would stop matching.
     *
     * <p>Two battles built from the same seed are identical, so if stepping one advances its random
     * stream the two next values must differ. A test that merely checked "a target was chosen" would
     * pass either way.
     */
    @Test
    void firstAliveTargetingConsumesNoRandomness() {
        Battle stepped = twinBattle();
        Battle untouched = twinBattle();
        stepped.startBattle();
        untouched.startBattle();

        // A roll drawn for the weighted selector would come from the same stream, so compare like for like.
        BattleDriver driver = new BattleDriver(stepped, new AutoPolicy(0, AutoPolicy.Targeting.FIRST_ALIVE));

        Assertions.assertAll(
                () -> Assertions.assertTrue(driver.step(), "the turn should be played"),
                () -> Assertions.assertEquals(untouched.getRng().nextLong(),
                        stepped.getRng().nextLong(),
                        "deterministic targeting must leave the random stream where the twin left it"));
    }

    /**
     * The same deterministic run twice reaches the same conclusion and the same turn count.
     */
    @Test
    void deterministicTargetingIsReproducible() {
        BattleDriver.Result first = deterministicRun();
        BattleDriver.Result second = deterministicRun();

        Assertions.assertAll(
                () -> Assertions.assertEquals(first.status(), second.status(),
                        "the status must be reproducible under deterministic targeting"),
                () -> Assertions.assertEquals(first.turns(), second.turns(),
                        "the turn count must be reproducible under deterministic targeting"));
    }

    private static BattleDriver seededBattle() {
        return BattleDriver.of(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L);
    }

    private static Battle twinBattle() {
        return new Battle(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("一号", 3000, 0, 100, 100),
                        Enemy.fromAttributes("二号", 3000, 0, 100, 100)),
                new Random(20260919L));
    }

    private static BattleDriver.Result deterministicRun() {
        return new BattleDriver(
                BattleFixture.battle(
                        List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                        List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100))),
                new AutoPolicy(1, AutoPolicy.Targeting.FIRST_ALIVE)).run();
    }
}
