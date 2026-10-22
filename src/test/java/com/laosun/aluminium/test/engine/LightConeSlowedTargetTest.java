package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "渊环 / Loop": "使装备者对减速状态下的敌方目标造成的伤害提高24/30/36/42/48%", judged both ways.
 *
 * <p>Preconditions, per disciplines 54-56: the victim is a high-HP monster (1002064, ~990k) so neither hit can kill it, and the
 * fixture asserts the hit is a real measurement rather than the whole remaining bar. The generator is pinned at no-crit.
 *
 * <p>The negative case is the one that has to be mutation-sensitive: dropping the condition from the content makes the boost
 * apply to an unslowed target, and this case is what notices.
 *
 * <p>⚠⚠ <b>What this fixture used to prove.</b> It synthesised a BUFF <b>named 减速</b> and applied it, which satisfied the
 * content's old spelling `target has_state 减速` - and that spelling was the bug: no such state exists, slow is a negative
 * SPEED modifier, and the rule was a silent no-op in every real battle. The fixture manufactured the very thing the condition
 * named, so it could only ever agree with itself (§6.3). It now slows the target the way the engine models it, which is what
 * `is_slowed` answers for.
 */
public class LightConeSlowedTargetTest {
    private static final int WEAPON_ID = 20011;
    private static final int WEARER = 1210;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002064;


    @Test
    public void theBoostAppliesAgainstASlowedTarget() {
        double rankOne = damage(1, true);
        double rankFive = damage(5, true);
        double ratio = rankFive / rankOne;
        Assertions.assertTrue(ratio > 1.13 && ratio < 1.30,
                "a slowed target takes more from rank 5 than rank 1: " + rankOne + " vs " + rankFive + " -> " + ratio);
    }

    @Test
    public void anUnslowedTargetGetsNothing() {
        double rankOne = damage(1, false);
        double rankFive = damage(5, false);
        Assertions.assertEquals(1.0, rankFive / rankOne, 1e-9,
                "without the state the condition is false for both ranks: " + rankOne + " vs " + rankFive);
    }

    private static double damage(int rank, boolean slowed) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Random noCrit = new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), noCrit);
        battle.startBattle();
        if (slowed) {
            // ⚠ Slow is a NEGATIVE SPEED modifier (that is what `is_slowed` reads), not a buff named 减速 - the old version of
            // this fixture applied such a buff, which is why it could never have caught the no-op.
            EffectSpec slow = new EffectSpec();
            TriggerSpecs.set(slow, "op", "MODIFY_ATTR");
            TriggerSpecs.set(slow, "attribute", "SPEED");
            TriggerSpecs.set(slow, "percent", -0.2);
            TriggerSpecs.set(slow, "turns", 5);
            TriggerSpecs.set(slow, "target", "target");
            ally.setTriggerTable(new TriggerTable(9301, List.of(TriggerSpecs.rule(
                    TriggerEvent.ALLY_ATTACK.name(), List.of(), slow))));
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 1, 0));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
        double dealt = before - enemy.getCurrentHp();
        Assertions.assertFalse(enemy.isDeath(), "the judged hit must not kill the victim");
        Assertions.assertTrue(dealt > 0 && dealt < 0.4 * before,
                "a real measurement, not the remaining bar: " + dealt + " of " + before);
        return dealt;
    }
}
