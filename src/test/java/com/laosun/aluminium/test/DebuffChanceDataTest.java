package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.skill.SkillData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P10-6: a skill's debuff base chance is read out of its description, and the plan's "it is the 3rd
 * {@code param_list} entry" is pinned as wrong.
 *
 * <p>The interesting number is <b>0.6</b> and <b>0.5</b> below: a rule that returned a constant, or
 * that read the wrong slot, cannot produce them. 姬子's technique is the trap that makes the plan's
 * index actively dangerous — index 3 there holds {@code 15} (a duration in seconds), which
 * {@code hitChance} would clamp to 1.0 and read as "always lands", i.e. a silent wrong answer rather
 * than a visible failure.
 */
public class DebuffChanceDataTest {

    /**
     * Five anchors, each read off the real description in {@code skills.json} — including the one
     * with an unusual layout ({@code 1108/7} writes {@code 有#2%<u>固定概率</u>}, with no 的) and the
     * two that are not 100% and not at index 0.
     */
    @Test
    public void theChanceIsThePlaceholderNextToTheProbabilityWording() {
        // 有<unbreak>#1[i]%</unbreak>的<u>基础概率</u>  →  param[0] = 1
        Assertions.assertEquals(1.0, SkillData.init(1003, 7).debuffChance(), 1e-9,
                "姬子 不完全燃烧: #1 → param_list[0]");
        // 有<unbreak>#1[i]%</unbreak>的<u>基础概率</u>  →  param[0] = 1
        Assertions.assertEquals(1.0, SkillData.init(1004, 7).debuffChance(), 1e-9,
                "瓦尔特 画地为牢: #1 → param_list[0]");
        // 有<unbreak>#2[i]%</unbreak><u>固定概率</u>  →  param[1] = 1  (no 的, and NOT index 0)
        Assertions.assertEquals(1.0, SkillData.init(1108, 7).debuffChance(), 1e-9,
                "桑博 你最闪亮: #2 → param_list[1]; this is what proves the slot is read from the text");
        // 有<color=#f29e38ff><unbreak>#4[i]%</unbreak></color>的<u>基础概率</u>  →  param[3] = 0.6
        Assertions.assertEquals(0.6, SkillData.init(1006, 4).debuffChance(), 1e-9,
                "银狼 等待程序响应: #4 → param_list[3] = 0.6");
        // 有<color=#f29e38ff><unbreak>#2[i]%</unbreak></color>的<u>基础概率</u>  →  param[1] = 0.5
        Assertions.assertEquals(0.5, SkillData.init(1307, 4).debuffChance(), 1e-9,
                "黑天鹅 无端命运的机杼: #2 → param_list[1] = 0.5 (the text names it twice, both #2)");
    }

    /**
     * The plan's index would have been silently wrong, so it is pinned the other way round: the value
     * that actually lives at {@code param_list[3]} for 姬子's technique is not a chance at all.
     */
    @Test
    public void thePlannedThirdEntryWouldHaveBeenNonsense() {
        SkillData himekoTechnique = SkillData.init(1003, 7);
        double plannedWrongValue = himekoTechnique.getSkills().getFirst().get(3);

        Assertions.assertEquals(15.0, plannedWrongValue, 1e-9,
                "the number the old plan would have read as a base chance (it is a duration in seconds)");
        Assertions.assertNotEquals(plannedWrongValue, himekoTechnique.debuffChance(), 1e-9,
                "reading param_list[3] would report a 1500% base chance, which hitChance clamps to "
                        + "'always lands' -- a wrong answer that looks like a correct one");
    }

    /**
     * A skill whose text states no chance must report <b>no chance</b>, not a fabricated 1.0.
     *
     * <p>波提欧's 【绝命对峙】 applies its effect with no wording about probability at all, so there is
     * nothing to roll. Returning 1.0 would be indistinguishable from "the text said 100%", and that
     * difference matters as soon as a caller decides whether to roll dice.
     */
    @Test
    public void aSkillWhoseTextStatesNoChanceReportsNoChance() {
        Assertions.assertNull(SkillData.init(1315, 2).debuffChance(),
                "波提欧 绝命对峙: the description states no probability, so there is nothing to read");
    }

    /**
     * The rule holds across <b>every</b> {@code Impair} skill in the data, not just the five anchors.
     *
     * <p>Two invariants, both measured rather than assumed: whatever is returned is a real chance
     * ({@code 0 < c <= 1}), and at least 14 of the 28 {@code Impair} skills state one. The second
     * number is the calibration's own coverage — if the textual rule regresses (say a future data
     * update rewords 基础概率), this goes red instead of quietly answering {@code null} for skills
     * that used to work.
     *
     * <p>The other half — "the skills that report nothing really state no chance" — is not asserted
     * here, because checking it would mean re-implementing the same textual rule inside the test and
     * would then pass no matter what the rule said. The five anchors above are what pins the rule.
     */
    @Test
    public void everyImpairSkillEitherStatesAChanceOrStatesNone() {
        List<String> statingAChance = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, Skill>> byCid : Constant.SKILLS.entrySet()) {
            for (Integer slot : byCid.getValue().keySet()) {
                SkillData data = SkillData.init(byCid.getKey(), slot);
                if (data.getEffect() != SkillEffectType.IMPAIR) {
                    continue;
                }
                Double chance = data.debuffChance();
                if (chance == null) {
                    continue;
                }
                if (chance <= 0 || chance > 1) {
                    problems.add(byCid.getKey() + "/" + slot + " -> " + chance);
                }
                statingAChance.add(byCid.getKey() + "/" + slot);
            }
        }

        Assertions.assertTrue(problems.isEmpty(),
                "these Impair skills produced a chance outside (0, 1], which means the wrong slot was read: "
                        + problems);
        Assertions.assertTrue(statingAChance.size() >= 14,
                "the textual rule must keep working for at least the 14 Impair skills measured to state a "
                        + "chance, but only " + statingAChance.size() + " did: " + statingAChance);
    }
}
