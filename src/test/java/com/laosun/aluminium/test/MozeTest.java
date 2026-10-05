package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1223 Moze, from his own file (2026-09-29, round 213): the Prey marker, the additional damage it draws, and the follow-up the Ultimate fires.
 */
public class MozeTest {
    private static final int MOZE = 1223;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ The additional damage needs the Prey mark, and its 30% is compared with a hand-built 60% reference in the same pipeline. */
    @Test
    public void theAdditionalDamageNeedsPrey() {
        double unmarked = additionalLoss(true, false);
        double marked = additionalLoss(true, true);
        double reference = additionalLoss(false, true);

        Assertions.assertEquals(0.0, unmarked, 1e-9,
                "「我方目标攻击【猎物】后」 -- without the mark, nothing");
        Assertions.assertTrue(marked > 0, "with the mark, the additional damage lands");
        Assertions.assertEquals(0.5, marked / reference, 0.05,
                "30% against a hand-built 60% reference: " + marked + " vs " + reference);
    }

    /** ⚠ The Ultimate fires the talent's follow-up, and the number is the talent's own 160%. */
    @Test
    public void theUltimateFiresTheTalentsFollowUp() {
        double fired = ultFollowUp();
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        double expected = moze.getAttribute(AttributeType.ATTACK).get() * 1.6;

        Assertions.assertTrue(fired > 0, "the follow-up must land when the event is fired");
        Assertions.assertEquals(expected, fired, expected * 0.15,
                "the talent's own 160% of his ATTACK: expected about " + expected + ", measured " + fired);
    }

    /** Fires a teammate's attack at the enemy; optionally marks it first, optionally uses a hand-built 60% reference rule. */
    private static double additionalLoss(boolean shipped, boolean mark) {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (!shipped) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 0.6);
            TriggerSpecs.set(effect, "element", "Thunder");
            TriggerSpecs.set(effect, "target", "target");
            moze.setTriggerTable(new TriggerTable(MOZE, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of("target has_state 猎物"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(moze, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            // Applied directly in BOTH paths, so the only difference between them is the percentage (round 207's lesson).
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("猎物", 1, true));
        }
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    /** Fires the Ultimate event WITHOUT casting the skill, so only the rule's follow-up damage is measured. */
    private static double ultFollowUp() {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(moze, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, moze, enemy, 0, 0);
        return before - enemy.getCurrentHp();
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
