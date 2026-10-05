package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "[协奏]状态结束前<b>不会进入自己的回合</b>且无法行动": a state that makes the unit's turns <b>pass without it</b> (2026-09-28).
 *
 * <p><b>Why it is not a control.</b> {@code ControlBuff} stops a unit from <i>acting</i> but still lets the turn
 * arrive - its DOTs tick and its turn-based buffs count down. "不会进入自己的回合" is a different sentence: the turn
 * itself does not happen, which is exactly what lets 知更鸟's countdown act <b>in her place</b>. Modelling one as the
 * other would silently change every "被冻结仍会掉血" reading in the corpus.
 *
 * <p><b>What is pinned here.</b> That a suspended unit takes no turn (no {@code TURN_START}, no DOT settlement), that
 * the flag rides on the state it belongs to (removing the state gives the turns back - no second switch to forget),
 * and that everybody else is unaffected.
 */
public class TurnSuspensionTest {
    private static final int MARCH = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** A suspended unit's turn passes without it: no TURN_START, no DOT tick. */
    @Test
    public void aSuspendedUnitTakesNoTurn() {
        Fixture f = new Fixture();
        f.suspendMarchFromARule();
        double attackBefore = f.attack(f.march);
        double hpBefore = f.march.getCurrentHp();

        f.takeHerTurn();

        Assertions.assertEquals(attackBefore, f.attack(f.march), 1e-6,
                "no TURN_START fired for her: the turn did not happen (a control would have let it)");
        Assertions.assertEquals(hpBefore, f.march.getCurrentHp(), 1e-6,
                "…and the burn on her did not tick either");
    }

    /** Note: Everyone else is untouched: the flag is about the carrier, not about the battle. */
    @Test
    public void everybodyElseStillActs() {
        Fixture f = new Fixture();
        f.suspendMarchFromARule();
        double attackBefore = f.attack(f.ally);

        f.takeAllysTurn();

        Assertions.assertTrue(f.attack(f.ally) > attackBefore, "the ally's own turn is normal");
    }

    /** Note: The flag rides on the state: removing the state gives the turns back, with no second switch to forget. */
    @Test
    public void removingTheStateGivesTheTurnsBack() {
        Fixture f = new Fixture();
        f.suspendMarchFromARule();
        f.march.getBuffManager().removeState("协奏");
        double attackBefore = f.attack(f.march);

        f.takeHerTurn();

        Assertions.assertTrue(f.attack(f.march) > attackBefore, "with the state gone she takes her turn again");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character march = CharacterFactory.create(MARCH, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Battle battle;

        private Fixture() {
            march.setTriggerTable(new TriggerTable(MARCH, List.of(turnStartMarker())));
            ally.setTriggerTable(new TriggerTable(ALLY, List.of(turnStartMarker())));
            battle = new Battle(List.of(march, ally), List.of(EnemyFactory.create(MONSTER, 90, 1)), fixed());
            battle.startBattle();
        }

        /** The state comes from a RULE, so the op path (`suspends_turns` on APPLY_BUFF) is what is being tested. */
        private void suspendMarchFromARule() {
            march.setTriggerTable(new TriggerTable(MARCH, List.of(turnStartMarker(), suspendingState())));
            battle.fireTriggers(TriggerEvent.BATTLE_START, march, march, 0, 0);
            Assertions.assertTrue(march.getBuffManager().suspendsTurns(),
                    "precondition: the state carries the flag");
            march.getBuffManager().addBuff(new DotBuff(ally, DamageElement.FIRE, 200, 3));
        }

        private double attack(Character unit) {
            return unit.getAttribute(AttributeType.ATTACK).get();
        }

        private void takeHerTurn() {
            turn(march);
        }

        private void takeAllysTurn() {
            turn(ally);
        }

        private void turn(Character unit) {
            battle.currentMove = battle.queue.snapshot().stream()
                    .filter(signal -> signal.getCanHit() == unit)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal for " + unit.getName()));
            battle.beforeMove();
        }
    }

    /** A rule that leaves a visible mark whenever a TURN_START reaches its owner's table. */
    private static TriggerSpec turnStartMarker() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.1);
        TriggerSpecs.set(effect, "turns", 1);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule(TriggerEvent.TURN_START.value(), null, effect);
    }

    /** "[协奏]状态结束前不会进入自己的回合" as the data spells it. */
    private static TriggerSpec suspendingState() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_BUFF");
        TriggerSpecs.set(effect, "buff", "协奏");
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "suspendsTurns", true);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("BATTLE_START", null, effect);
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
