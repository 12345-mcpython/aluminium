package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1402 阿格莱雅's talent: 「攻击处于【间隙织线】状态下的敌人后，额外造成等同于阿格莱雅攻击力30%的雷属性附加伤害」.
 *
 * <p>⚠ Both directions, with 「衣匠在场时」 held fixed: the same summon is out in both runs, so the only difference is the state
 * on the target. The clause's other half -- the state is APPLIED by her attacks -- is a separate sentence and is not claimed here.
 */
public class AglaeaFissureTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String STATE = "间隙织线";

    @Test
    public void theAdditionalDamageLandsOnlyOnAThreadedTarget() {
        double threaded = skillDamage(true);
        double plain = skillDamage(false);
        Assertions.assertTrue(plain > 0, "precondition: the skill lands: " + plain);
        Assertions.assertTrue(threaded > plain,
                "the additional damage must land on a 【间隙织线】 target: " + plain + " -> " + threaded);
        double extra = threaded - plain;
        double attack = attackOf();
        // \u26a0 The arithmetic of a flat addend is pinned by `AddDamageOpTest` (it lands in the BASE layer, so defence and the
        // other zones apply AFTERWARDS). What this test pins is the CONTENT: the clause fires, and its size is 30% of her ATK
        // BEFORE mitigation -- an upper bound. Measured: 122.145 against 209.563 unmitigated.
        Assertions.assertTrue(extra > 0, "the additional damage must be positive: " + extra);
        Assertions.assertTrue(extra < 0.30 * attack,
                "and it cannot exceed the unmitigated 30% of her ATK (" + attack + "): " + extra);
    }

    private static double attackOf() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        return aglaea.getAttribute(AttributeType.ATTACK).get();
    }

    /** Her skill's damage against a victim, with the state applied or not. */
    private static double skillDamage(boolean threaded) {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        if (threaded) {
            EffectSpec state = new EffectSpec();
            TriggerSpecs.set(state, "op", "APPLY_BUFF");
            TriggerSpecs.set(state, "buff", STATE);
            TriggerSpecs.set(state, "turns", 5);
            TriggerSpecs.set(state, "target", "target");
            ally.setTriggerTable(new TriggerTable(9701, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of(), state))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), noCrit);
        battle.startBattle();
        // 「衣匠在场时」 -- held fixed, and it is also the condition the rule needs.
        battle.summonMemosprite(aglaea);
        if (threaded) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0);
        }
        double before = enemy.getCurrentHp();
        // ⚠ COMMON, not SKILL: her Skill heals/summons the 衣匠 (no damage at all), so her attack is the basic one.
        battle.castImmediate(aglaea.getSkills().get(SkillType.COMMON), aglaea, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.5 * before,
                "a real measurement, not the whole bar: " + dealt + " of " + before);
        return dealt;
    }
}
