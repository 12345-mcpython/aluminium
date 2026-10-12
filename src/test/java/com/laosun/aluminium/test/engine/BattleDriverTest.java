package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.battle.AutoPolicy;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.battle.BattleListener;
import com.laosun.aluminium.models.battle.SkillPolicy;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
 */
public class BattleDriverTest {

    /**
     * A battle that can actually be won: a real character with a real kit against a killable enemy.
     */
    @Test
    void aBattleAgainstAKillableEnemyIsDecided() {
        Character hero = CharacterFactory.create(1109, 80, false, null, null, 0);
        Enemy foe = Enemy.fromAttributes("训练假人", 800, 0, 100, 100);

        BattleDriver.Result result = BattleDriver.of(List.of(hero), List.of(foe), 20260919L).run();

        Assertions.assertTrue(result.isDecided(),
                "the battle should reach a terminal state, got " + result.outcome()
                        + " after " + result.turns() + " turns");
        Assertions.assertEquals(Battle.Status.WIN, result.status(),
                "a lone hero against one weak enemy should win");
        Assertions.assertTrue(result.turns() > 0, "at least one turn must have been taken");
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

        Assertions.assertEquals(BattleDriver.Outcome.TURN_LIMIT, result.outcome(),
                "an undecidable battle must be reported as a turn limit, not as finished");
        Assertions.assertFalse(result.isDecided(), "a turn limit is not a decision");
        Assertions.assertEquals(12, result.turns(), "the ceiling should be honoured exactly");
    }

    /**
     * The same seed reaches the same conclusion, so a failure can be reproduced.
     */
    @Test
    void theSameSeedDecidesTheSameWayTwice() {
        BattleDriver.Result first = BattleDriver.of(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L).run();
        BattleDriver.Result second = BattleDriver.of(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L).run();

        Assertions.assertEquals(first.status(), second.status(), "status must be reproducible");
        Assertions.assertEquals(first.turns(), second.turns(), "turn count must be reproducible");
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

        Assertions.assertTrue(driver.step(), "the first step should find an actor");
        Assertions.assertFalse(battle.isOver(),
                "one turn against a healthy enemy must not decide the battle");
        Assertions.assertTrue(driver.step(), "the second step should also find an actor");
        Assertions.assertFalse(battle.isOver(), "still undecided after two turns");
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

        Assertions.assertEquals(BattleDriver.Outcome.TURN_LIMIT, result.outcome());
        Assertions.assertEquals(8, result.turns(),
                "every step must have advanced the bar even though no action was taken");
    }

    /**
     * A listener sees every turn, in order, and each start is matched by an end.
     *
     * <p>The pairing is the part worth pinning: a front end that reports "X acts" on a start and never
     * hears the end would leave its own state half-updated, and the driver taking over the loop is
     * exactly what makes such an omission possible.
     */
    @Test
    void aListenerSeesEveryTurnStartPairedWithAnEnd() {
        List<String> events = new ArrayList<>();
        BattleDriver driver = BattleDriver.of(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L)
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
                });

        BattleDriver.Result result = driver.run();

        Assertions.assertEquals("start", events.getFirst(), "the battle start comes first");
        Assertions.assertTrue(events.getLast().startsWith("over:"),
                "the battle end comes last, got " + events.getLast());

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
        Assertions.assertEquals(result.turns(), starts, "one start per turn taken");
        Assertions.assertEquals(starts, ends, "every start must be paired with an end");
    }

    /**
     * A battle driven with a listener decides exactly as one driven without, so observing cannot
     * change the outcome.
     */
    @Test
    void attachingAListenerDoesNotChangeTheOutcome() {
        BattleDriver.Result silent = BattleDriver.of(
                List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L).run();

        BattleDriver.Result watched = BattleDriver.of(
                        List.of(CharacterFactory.create(1109, 80, false, null, null, 0)),
                        List.of(Enemy.fromAttributes("训练假人", 800, 0, 100, 100)), 20260919L)
                .withListener(new BattleListener() {
                    @Override
                    public void onTurnStart(CanHit actor, int turn) {
                        // a listener that reads state must not perturb the random stream
                        actor.getName();
                    }
                })
                .run();

        Assertions.assertEquals(silent.status(), watched.status());
        Assertions.assertEquals(silent.turns(), watched.turns(),
                "narration must not consume the battle's randomness");
    }
}
