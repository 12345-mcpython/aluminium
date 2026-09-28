package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
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
 * 佩拉 (1106), from her own file (2026-09-28): the two 「处于负面效果」 clauses and the party's effect hit rate.
 *
 * <p><b>What it needed.</b> Nothing new — {@code target_debuff_count} is the numerical variable that reads how many
 * negative buffs the event's target carries, and this file is its first shipped reader (both the talent's energy and 行迹
 * 痛击's damage bonus are gated on it). What is <b>registered</b> is 战技's 「解除敌方增益」 (the engine's `DISPEL` cleanses
 * our own side's debuffs; removing an <i>enemy's</i> buff is the opposite direction) and the ultimate's 【通解】, which is a
 * state with a 100% <b>base chance</b> (a roll the `APPLY_BUFF` path does not have).
 */
public class PelaDebuffTest {
    private static final int PELA = 1106;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ Both 「处于负面效果」 clauses are gated on the target's debuff count. */
    @Test
    public void theDebuffConditionsAreOnTheRules() {
        TriggerTable table = TriggerTables.of(PELA);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK), "the talent's energy trace");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "行迹 痛击");

        // ⚠ Two separate fixtures: adding a DOT is not undone, so reusing one enemy would leave the "no debuff" check
        // looking at a debuffed target (an earlier version of this case did exactly that).
        Fixture debuffed = new Fixture();
        Assertions.assertFalse(table.matching(TriggerEvent.DEALING_DAMAGE, debuffed.ctx(true)).isEmpty(),
                "with a debuff on the target, 痛击 matches");

        Fixture clean = new Fixture();
        Assertions.assertTrue(table.matching(TriggerEvent.DEALING_DAMAGE, clean.ctx(false)).isEmpty(),
                "⚠ with none it does not: 「对处于**负面效果**的敌方目标」");
    }

    /** 行迹 秘策 hands the whole side the effect-hit rate, and the file says so. */
    @Test
    public void herTraceRaisesThePartysEffectHitRate() {
        Fixture f = new Fixture();
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.EFFECT_HIT_RATE).get() > 0,
                "「佩拉在场时，我方全体的效果命中提高10%」");
        Assertions.assertEquals(1, TriggerTables.of(PELA).ruleCount(TriggerEvent.BATTLE_START) - 1,
                "census: her file has the 秘策 rule and the level-convention rule");
    }

    /**
     * ⚠ The direction is the whole point: her Skill strips an enemy's <b>benefit</b> (a timed shield) and leaves the
     * <b>negative</b> effects it carries alone.
     */
    @Test
    public void herSkillStripsABenefitAndNotADebuff() {
        Fixture f = new Fixture();
        f.enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff("测试增益", 2));
        f.enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(f.pela,
                com.laosun.aluminium.enums.DamageElement.FIRE, 10, 2));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("测试增益"), "precondition: the enemy has a shield");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("灼烧"), "precondition: and a DOT");

        f.battle.castImmediate(f.pela.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), f.pela,
                List.of(f.enemy));

        Assertions.assertFalse(f.enemy.getBuffManager().hasState("测试增益"),
                "「解除指定敌方单体的1个增益效果」 -- the shield went");
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("灼烧"),
                "⚠ …and the DOT stayed: REMOVE_BUFF is the mirror of DISPEL, not a second DISPEL");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character pela = CharacterFactory.create(PELA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(pela, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        /** The rule's context, with the target carrying a debuff or not. */
        private TriggerTable.TriggerContext ctx(boolean debuffed) {
            if (debuffed) {
                enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(pela,
                        com.laosun.aluminium.enums.DamageElement.FIRE, 10, 2));
            }
            return new TriggerTable.TriggerContext(pela, pela, enemy, 0, 0, null, battle);
        }
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
