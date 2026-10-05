package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A weakness that expires (`65f143b`): `ADD_ELEMENTAL_WEAKNESS` with `turns` goes through {@code Enemy.addWeakness(element, turns)},
 * and the timer counts down at the end of the target's own turn (beside `TURN_END` at `Battle:1203`).
 *
 * <p>Note: This judge must drive the real turn loop (`battle.stepForward()`). If it only called `enemy.tickTimedWeaknesses()`,
 * the `Enemy` half would be covered, while a mistake in the wiring at `Battle:1203` would not go red - - this section has seen "the judge covers only half" far too many times.
 *
 * <p>Note: Both assertions are indispensable: "gone in the end" proves the countdown happens; "not gone on the first step" proves the count is right (otherwise writing
 * `turns: 2` as `turns: 0` would also make the first one green).
 */
public class TimedWeaknessTest {
    private static final int WEARER = 1006;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final int TURNS = 2;

    @Test
    public void anInsertedWeaknessExpiresInItsOwnTurns() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        // Note: Do not hard-code the element: pick one this enemy does not have. Hard-coding it would make the judge depend on its attribute table
        // (measured: 1002011 already has Fire, and the first run was stopped by exactly this precondition).
        DamageElement chosen = null;
        for (DamageElement each : DamageElement.values()) {
            if (!enemy.isWeakTo(each)) {
                chosen = each;
                break;
            }
        }
        Assertions.assertNotNull(chosen, "this enemy is weak to every element, which cannot happen");
        EffectSpec add = new EffectSpec();
        TriggerSpecs.set(add, "op", "ADD_ELEMENTAL_WEAKNESS");
        TriggerSpecs.set(add, "element", chosen.name());
        TriggerSpecs.set(add, "turns", TURNS);
        TriggerSpecs.set(add, "target", "target");
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), add);
        TriggerSpecs.set(rule, "id", "probe_timed_weakness");
        TriggerSpecs.set(rule, "when", List.of("actor == self"));
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));

        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        Assertions.assertFalse(enemy.isWeakTo(chosen), "the enemy must not already have it");

        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        Assertions.assertTrue(enemy.isWeakTo(chosen), "the insertion itself must land");

        int steps = 0;
        while (enemy.isWeakTo(chosen) && !battle.isOver() && steps < 60) {
            battle.stepForward();      // Note: It only sets currentMove to the next actor (measured: it does not execute the turn)
            battle.afterMove();        // Ends that action - - the timed weakness counts down inside afterMove
            steps++;
        }
        System.out.println("[timed] " + chosen + " weakness gone after " + steps + " steps (declared turns=" + TURNS
                + ", over=" + battle.isOver() + ")");
        Assertions.assertFalse(enemy.isWeakTo(chosen),
                "a timed weakness must expire -- " + steps + " steps and it is still there");
        Assertions.assertTrue(steps > 1,
                "but it must not fall on the very first step: the count would be wrong, not just the ticking");
    }

    /**
     * Note: Split the two halves apart: call `Enemy.tickTimedWeaknesses()` directly (it is `public`).
     *
     * <p>The one above walks the real turn loop, and it did not expire in 60 steps, so there are two possibilities: `Enemy`'s countdown itself is broken,
     * or that loop never reached the enemy's turn at all. This one asks only about the first half - - Note: if it is green, the problem must be in the wiring.
     */
    @Test
    public void theEnemySideRunsDownOnItsOwn() {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        DamageElement chosen = null;
        for (DamageElement each : DamageElement.values()) {
            if (!enemy.isWeakTo(each)) {
                chosen = each;
                break;
            }
        }
        Assertions.assertNotNull(chosen);
        enemy.addWeakness(chosen, TURNS);
        Assertions.assertTrue(enemy.isWeakTo(chosen), "declared with turns, so it is a weakness right now");
        System.out.println("[timed] direct: inserted " + chosen + " with turns=" + TURNS
                + " count=" + enemy.weaknessCount());

        enemy.tickTimedWeaknesses();
        Assertions.assertTrue(enemy.isWeakTo(chosen), "after one tick it still has one turn left");
        enemy.tickTimedWeaknesses();
        System.out.println("[timed] direct: after two ticks weak=" + enemy.isWeakTo(chosen)
                + " count=" + enemy.weaknessCount());
        Assertions.assertFalse(enemy.isWeakTo(chosen), "two ticks and a turns=2 weakness is gone");
    }
}
