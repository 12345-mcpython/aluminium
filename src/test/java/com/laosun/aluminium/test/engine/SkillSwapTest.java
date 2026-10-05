package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.SkillSwapBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code REPLACE_SKILL} (2026-09-28): a rule can swap one of its owner's skill slots for a data row.
 *
 * <p>Reader: 1301 Gallagher's ultimate "并将下一次普攻强化为[酒花奔涌]" - the enhanced attack is row <b>130108</b>, and
 * {@code SkillData.init} resolves rows <b>by id</b> ({@code Constant.SKILLS.get(cid).get(skillID)}), which is why the engine
 * could always load it: round 98's note claiming otherwise came from {@code DefaultSkill}'s own stale javadoc rather than from
 * the code.
 *
 * <p>Note: The observation is the <b>slot's own skill object identity</b>: swapping puts a different object in the slot and ending
 * the swap puts the original back. That is deliberately accessor-free - an earlier version read
 * {@code getData().getParamList()}, which is not what that class calls it - and identity is exactly the question being asked
 * ("is the slot's skill still the one it was?").
 */
public class SkillSwapTest {
    private static final int GALLAGHER = 1301;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int ENHANCED_ROW = 130108;

    /** Note: The swap installs a different skill into the slot, and it is that row (the buff says which). */
    @Test
    public void theSwapInstallsTheNamedRow() {
        Fixture f = new Fixture();
        Skill before = f.slot();

        SkillSwapBuff swap = f.swap();

        Assertions.assertNotSame(before, f.slot(), "the slot holds a different skill object while the swap lasts");
        Assertions.assertEquals(ENHANCED_ROW, swap.getReplacement().getSkillSlot(),
                "…and it is the row the rule named (130108 for 加拉赫's enhanced attack)");
    }

    /** Note: It is a swap, not a permanent replacement: the buff captured the original and puts it back. */
    @Test
    public void endingTheSwapRestoresTheOriginal() {
        Fixture f = new Fixture();
        Skill before = f.slot();

        SkillSwapBuff swap = f.swap();
        swap.removeBuff(f.gallagher);

        Assertions.assertSame(before, f.slot(), "the original skill is back in the slot");
    }

    /** Note: A swap needs a row id: without one the rule is refused instead of silently doing nothing. */
    @Test
    public void aMissingRowIdIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "REPLACE_SKILL");
        TriggerSpecs.set(effect, "skill", "COMMON");
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "self");
        TriggerSpec wrong = TriggerSpecs.rule("KILL", List.of("actor == self"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(GALLAGHER, List.of(wrong)));
        Assertions.assertTrue(refused.getMessage().contains("skill_id"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character gallagher = CharacterFactory.create(GALLAGHER, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(gallagher, ally), List.of(enemy), fixed());

        private Fixture() {
            battle.startBattle();
        }

        private Skill slot() {
            return gallagher.getSkills().get(SkillType.COMMON);
        }

        /** Fires a hand-made REPLACE_SKILL rule and returns the buff that landed. */
        private SkillSwapBuff swap() {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "REPLACE_SKILL");
            TriggerSpecs.set(effect, "skill", "COMMON");
            TriggerSpecs.set(effect, "skillId", ENHANCED_ROW);
            TriggerSpecs.set(effect, "turns", 2);
            TriggerSpecs.set(effect, "target", "self");
            gallagher.setTriggerTable(new TriggerTable(GALLAGHER,
                    List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), effect))));
            battle.fireTriggers(TriggerEvent.KILL, gallagher, gallagher, 0, 0);

            List<SkillSwapBuff> swaps = gallagher.getBuffManager().allBuffsOf(SkillSwapBuff.class);
            Assertions.assertEquals(1, swaps.size(), "precondition: the swap landed");
            return swaps.get(0);
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
