package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code IGNORE_WEAKNESS}: 「本次攻击**无视弱点属性**削减敌方的韧性」 (1214 Xueyi's Ultimate).
 *
 * <p>The cast path is what this pins: a flag on the unit, consumed by the ONE place that turns a nominal toughness
 * reduction into a settled one.
 */
public class IgnoreWeaknessOnCastTest {

    private static final int OWNER = 1003;   // Herta: a Fire skill that really hits
    private static final int LEVEL = 80;

    @Test
    public void aMarkedCastTakesToughnessOffANonWeakness() {
        double without = stanceAfterCast(false);
        double with = stanceAfterCast(true);
        System.out.println("[ignore-weakness] " + without + " -> " + with);
        Assertions.assertEquals(0.0, without, 1e-9, "precondition: the gate refuses a non-weakness hit");
        Assertions.assertEquals(60.0, with, 1e-9, "「无视弱点属性削减韧性」: the whole stance lands anyway");
    }

    @Test
    public void theFlagIsSpentByTheCastNotForever() {
        Character owner = characterWith(markingRule());
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);   // Thunder/Wind: Quantum is NOT a weakness
        Battle battle = battle(owner, enemy);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, owner, owner, 0, 0));
        Assertions.assertTrue(owner.ignoresWeaknessThisCast(), "the rule marked the next cast");
        // Two casts: only the first may ignore the gate.
        battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(enemy));
        Assertions.assertFalse(owner.ignoresWeaknessThisCast(), "the cast spent the mark");
    }

    /** @return the enemy's stance after one Skill cast, optionally marked */
    private static double stanceAfterCast(boolean marked) {
        Character owner = characterWith(markingRule());
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);
        Battle battle = battle(owner, enemy);
        if (marked) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TURN_START, owner, owner, 0, 0));
        }
        double before = enemy.getStance();
        battle.castImmediate(owner.getSkills().get(SkillType.SKILL), owner, List.of(enemy));
        return before - enemy.getStance();
    }

    private static TriggerSpec markingRule() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "IGNORE_WEAKNESS");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule(TriggerEvent.TURN_START.name(), null, "probe", effect);
    }

    private static Character characterWith(TriggerSpec... rules) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        // 1003 attacks with FIRE; the target below is weak to Thunder/Wind only.
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rules)));
        return owner;
    }

    private static Battle battle(Character owner, Enemy enemy) {
        Battle battle = new Battle(new ArrayList<>(List.of(owner, CharacterFactory.create(1002, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}