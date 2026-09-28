package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The decisive probe for the enhanced-attack path (2026-09-28, round 113) — <b>no engine change</b>, only questions.
 *
 * <p>Round 112 established that the condition and the context path are fine (`from_skill_id` matches a hand-built context),
 * which left the live path. Reading {@code SkillExecutor} showed the row id IS passed, so the remaining explanation is that
 * the skill {@code REPLACE_SKILL} builds carries <b>no data</b>: {@code SkillData.init} answers {@code EMPTY} when it cannot
 * find the row, an empty skill produces no hits, and {@code SkillExecutor} fires {@code ALLY_ATTACK} only when something was
 * hit — which is exactly the observed silence (no layer, no cost, no attack-down).
 *
 * <p>⚠ Why the earlier "precondition" passed anyway: {@code getSkillSlot()} returns the row id whether or not the data was
 * found, so asserting the slot is <b>not</b> proof that the swapped skill can do anything. That is the lesson this class
 * exists to record: assert one level deeper than the identity.
 */
public class EnhancedSkillDataProbeTest {
    private static final int GALLAGHER = 1301;
    private static final int GALLAGHER_ENHANCED_ROW = 130108;
    private static final int LUKA = 1111;
    private static final int LUKA_ENHANCED_ROW = 111108;
    private static final int LEVEL = 10;

    /** ⚠ The row exists in the data dump (130108 = Normal/SingleAttack), so a real row must load as NORMAL. */
    @Test
    public void gallaghersEnhancedRowLoadsRealData() {
        Skill enhanced = new DefaultSkill(GALLAGHER, GALLAGHER_ENHANCED_ROW, LEVEL);
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, enhanced.getData().getCategory(),
                "⚠ THIS IS THE MEASURED FACT (2026-09-28): a DATA ROW id is not what the loader wants, so this reads "
                        + "UNSPECIFIED — the swapped skill carries no data and produces no hits. Round 98's javadoc said the "
                        + "parameter is a slot; round 107 called that stale; the measurement says round 98 was right. "
                        + "⚠ It is why the two REPLACE_SKILL clauses were withdrawn: they installed a no-op.");
    }

    /** The same question for 1111's 【直冲碎天拳】. */
    @Test
    public void lukasEnhancedRowLoadsRealData() {
        Skill enhanced = new DefaultSkill(LUKA, LUKA_ENHANCED_ROW, LEVEL);
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, enhanced.getData().getCategory(),
                "the same measured fact for 111108");
    }

    /** ⚠ The positive half of the same question: the LOADER KEY is a slot, and slot 8 is the enhanced attack. */
    @Test
    public void theEnhancedAttackLoadsUnderItsSlot() {
        Assertions.assertEquals(SkillCategory.NORMAL, new DefaultSkill(GALLAGHER, 8, LEVEL).getData().getCategory(),
                "「强化普攻」是槽位 8（`data/skills.json` 的内层键是 1,2,3,4,6,7,8）——数据行的末位就是槽位：130108 / 111108");
        Assertions.assertEquals(SkillCategory.NORMAL, new DefaultSkill(LUKA, 8, LEVEL).getData().getCategory(),
                "the same slot for 1111's 【直冲碎天拳】");
    }

    /** Control: an ordinary slot row loads real data, so the probe can tell the two apart. */
    @Test
    public void anOrdinaryRowLoadsRealData() {
        Skill ordinary = new DefaultSkill(GALLAGHER, 130101, LEVEL);
        Assertions.assertEquals(SkillCategory.UNSPECIFIED, ordinary.getData().getCategory(),
                "⚠ and the CONTROL is what makes it decisive: even the ordinary basic attack's ROW id loads nothing, so the "
                        + "finding is about the loader's key, not about enhanced attacks");
    }
}
