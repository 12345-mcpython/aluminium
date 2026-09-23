package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-1 acceptance: round-based action value.
 *
 * <pre>
 * first round total action value 150, then 100 per round      ⇒ each unit's **first cycle** = 10000/speed × 1.5
 * </pre>
 *
 * <p>Meaning: a unit with speed 100 waits 150 in the first round (from the second lap it acts every
 * 100); a unit with speed 200 waits 75 in the first round — so **a fast unit gets an extra action
 * within the first round**
 * (a unit with speed 240 has a cycle of 41.67, so it can act 3 times within the first round's 150).
 */
public class QueueRoundTest {
    private static final double EPS = 1e-9;

    @Test
    public void firstRoundIsOneAndAHalfCycles() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(150, q.timeUntilNext(), EPS, "first round: 10000/100 × 1.5 = 150");
        Assertions.assertEquals(1, q.getRound(), "nothing has moved yet, it is round 1");
    }

    @Test
    public void laterRoundsAreExactlyOneCycle() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(150, q.move(), EPS, "first round 150");
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS, "100 per round from round 2 on");
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS);
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS);
    }

    @Test
    public void fastUnitActsBeforeTheFirstRoundEnds() {
        // speed 200: first round 75, so it can act twice within 150 (t=75, t=125)
        Queue q = new Queue(List.of(character("speed200", 200)));

        Assertions.assertEquals(75, q.move(), EPS, "the first action is at 75");
        q.setTopZero();
        Assertions.assertEquals(50, q.move(), EPS, "the second is at 125 (75 + 50)");
        q.setTopZero();
        Assertions.assertEquals(50, q.move(), EPS, "the third is at 175 — round 2 has already begun");
    }

    @Test
    public void firstRoundMultiplierDoesNotChangeTheOrder() {
        // everyone ×1.5 ⇒ the first-round order matches the pure speed order
        Character slow = character("slow", 100);
        Character mid = character("mid", 150);
        Character fast = character("fast", 200);
        Queue q = new Queue(List.of(slow, mid, fast));

        Assertions.assertEquals(fast, q.peekNext());
        Assertions.assertEquals(75, q.timeUntilNext(), EPS);
        q.move();
        q.setTopZero();
        Assertions.assertEquals(mid, q.peekNext(), "the second is the 150-speed one (cycle 66.67)");
    }

    @Test
    public void roundCounterFollowsElapsedTime() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(1, q.getRound(), "elapsed = 0");
        q.move();                                    // elapsed = 150 → the first round ends
        Assertions.assertEquals(1, q.getRound(), "elapsed = 150 still counts as round 1");
        q.setTopZero();

        q.move();                                    // elapsed = 250
        Assertions.assertEquals(2, q.getRound(), "elapsed = 250 → round 2");
        q.setTopZero();

        q.move();                                    // elapsed = 350
        Assertions.assertEquals(3, q.getRound(), "elapsed = 350 → round 3");
    }

    @Test
    public void battleExposesTheRound() {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);      // speed 132 > 100, so it acts first
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));

        Assertions.assertEquals(1, battle.getRound(), "it is round 1 at the start");
        battle.stepForward();
        Assertions.assertEquals(1, battle.getRound(), "Ice Edge acts at 75.76 in the first round at 132 speed, still round 1");
    }

    @Test
    public void midBattleJoinerIsNotStretched() {
        // P7-1 only affects initialize(): entering mid-battle uses the normal cycle (deliberate — see the note on addCombatant)
        Queue q = new Queue(List.of(character("speed100", 100)));
        q.move();
        q.setTopZero();                              // elapsed = 150

        Character joiner = character("joiner", 100);
        q.addCombatant(joiner);

        // joiner is queued at elapsed + 100 = 250; the current heap top is speed100 (elapsed+100 = 250) —
        // both have the same value, so which goes first is decided by the heap; here we only assert that
        // "joiner waits 100, not 150"
        Assertions.assertEquals(100, q.getTimeRemaining(q.getHeap().stream()
                .filter(s -> s.getCanHit() == joiner).findFirst().orElseThrow()), EPS);
    }

    // ==================================================================

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }
}
