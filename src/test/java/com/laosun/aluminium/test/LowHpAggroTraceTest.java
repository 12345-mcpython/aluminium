package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「若当前生命值百分比小于等于50%，则被敌方目标攻击的概率降低」 — one sentence, three characters.
 *
 * <p>The gate is a STATE, not an event, so it is spelled the way the engine already spells state-scoped modifiers: apply
 * it when HP crosses below (HP_LOST) and take it off when HP comes back (HEALED). That pair is the engine's equivalent
 * of the game's own OnHPChange re-decision, which Dan Heng's ability config shows explicitly.
 *
 * <p>Four steps are pinned per character: nothing at full HP, x0.5 below the gate, no stacking on a second loss (a
 * named modifier has a cap of one), and back to the original weight once healed above it.
 */
public class LowHpAggroTraceTest {
    private static final double EPS = 1e-9;
    private static final int[] OWNERS = {1002, 1102, 1206};
    private static final int ALLY = 1210;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theLowHpTraceHalvesTheWeightAndComesBackOnHealing() {
        for (int cid : OWNERS) {
            Character owner = CharacterFactory.create(cid, LEVEL);
            Character ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));
            battle.startBattle();
            double full = battle.aggroOf(owner);

            Assertions.assertEquals(full, battle.aggroOf(owner), EPS,
                    "at full HP the trace is not up (cid " + cid + ")");

            dropBelowHalf(battle, owner, enemy);
            Assertions.assertEquals(full * 0.5, battle.aggroOf(owner), EPS,
                    "\u300c\u5219\u88ab\u654c\u65b9\u76ee\u6807\u653b\u51fb\u7684\u6982\u7387\u964d\u4f4e\u300d: "
                            + "1 + (-0.5) => x0.5 (cid " + cid + ")");

            // A second loss while already below the gate must not stack a second modifier.
            battle.fireTriggers(TriggerEvent.HP_LOST, enemy, owner, 0, 0);
            Assertions.assertEquals(full * 0.5, battle.aggroOf(owner), EPS,
                    "re-applying a named modifier must not stack (cid " + cid + ")");

            healAboveHalf(battle, owner);
            Assertions.assertEquals(full, battle.aggroOf(owner), EPS,
                    "healed above the gate: REMOVE_STATE takes the named modifier off (cid " + cid + ")");
        }
    }

    /** Damages the unit until its HP is at or below half, leaving it alive. */
    private static void dropBelowHalf(Battle battle, Character unit, Enemy enemy) {
        int guard = 0;
        while (unit.getCurrentHp() / unit.getMaxHp() > 0.5 && guard++ < 40) {
            battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL,
                    unit.getMaxHp() * 0.2));
        }
        Assertions.assertTrue(unit.getCurrentHp() > 0, "the fixture must leave the unit alive");
        Assertions.assertTrue(unit.getCurrentHp() / unit.getMaxHp() <= 0.5, "and at or below the gate");
    }

    /** Heals the unit back above half. */
    private static void healAboveHalf(Battle battle, Character unit) {
        int guard = 0;
        while (unit.getCurrentHp() / unit.getMaxHp() <= 0.5 && guard++ < 40) {
            battle.heal(unit, unit, unit.getMaxHp());
        }
        Assertions.assertTrue(unit.getCurrentHp() / unit.getMaxHp() > 0.5, "back above the gate");
    }
}
