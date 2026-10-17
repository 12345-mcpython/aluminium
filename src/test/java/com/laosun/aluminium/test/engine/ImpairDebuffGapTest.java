package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Constant;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.skill.DefaultSkill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An {@code Impair} skill's <b>debuff is not dispatched</b>, and the reason is data rather than code - this class pins
 * that state so it cannot change silently.
 *
 * <h2>The gap, measured</h2>
 * {@code skill_effects.json} carries an effect row for the skills whose work the executor can do ({@code Restore} a heal,
 * {@code Defence} a shield, {@code Rules} "the rule table handles it"), and it has <b>no</b> {@code Impair} entry at all.
 * The missing piece is not "how much", it is <b>which debuff</b>: an impair row states a base chance and a duration, and
 * the state it applies is only in the prose (「陷入【吞火】状态」 / 「风化/裂伤/灼烧/触电其中1种」). Reading that into a dispatch
 * would be a guess with a state name in it - the one thing this project refuses.
 *
 * <p>So the two halves are pinned separately:
 * <ol>
 *   <li>{@link #noImpairSkillCarriesAnEffectRow()} - no shipped {@code Impair} skill has a row, so a cast of one
 *       delivers nothing and says so through the executor's own "not dispatched" report. The moment a row appears (a
 *       regenerated data set that names the debuff), this test goes red and the dispatch has its input;</li>
 *   <li>{@link #theSkillsThatNameAnInBattleStateAreTheRegisteredGap()} - the census of skills whose description
 *       <b>does</b> name an in-battle state, i.e. the work the missing rows would unlock. Change the list and it is a
 *       decision, not a surprise.</li>
 * </ol>
 *
 * <p>⚠ The reader {@code SkillData.debuffChance(int)} exists for that future dispatch. It is <b>level-aware</b> (the row
 * of the skill's own level) and it has no caller today - which is why this class says so out loud instead of leaving the
 * gap to be discovered.
 */
public class ImpairDebuffGapTest {

    /**
     * The in-battle states the shipped {@code Impair} descriptions name. Map states (目盲 / 晕眩 / 魄散 / 醉心) are
     * deliberately not counted: this engine has no overworld, so those sentences describe something it does not model.
     */
    private static final Pattern IN_BATTLE_STATE = Pattern.compile("【(吞火|烬煨)】|(风化|裂伤|灼烧|触电)");

    /**
     * Part 1: no {@code Impair} skill has an effect row, so no cast of one can deliver a debuff.
     */
    @Test
    public void noImpairSkillCarriesAnEffectRow() {
        List<String> withRows = new ArrayList<>();
        int impairSkills = 0;
        for (Map.Entry<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> byCid : Constant.SKILLS.entrySet()) {
            int cid = byCid.getKey();
            for (int slot : byCid.getValue().keySet()) {
                SkillData data = SkillData.init(cid, slot);
                if (data == null || data.getEffect() != SkillEffectType.IMPAIR) {
                    continue;
                }
                impairSkills++;
                // The row lookup the executor itself uses (`dispatchNonDamaging` → `deliverableSpec`), through the one
                // public accessor: no row means "the cast delivers nothing".
                if (com.laosun.aluminium.data.SkillEffects.forSkill(new DefaultSkill(cid, slot, 1)) != null) {
                    withRows.add(cid + "/" + slot);
                }
            }
        }
        Assertions.assertTrue(impairSkills > 20, "expected the shipped Impair skills, found " + impairSkills);
        Assertions.assertEquals(List.of(), withRows,
                "these Impair skills now have an effect row in skill_effects.json: the dispatch has its input, so wire "
                        + "it (the executor's non-damaging branch) instead of leaving the cast a no-op");
    }

    /**
     * Part 2: the skills whose description names an in-battle state - the content the missing rows would unlock.
     *
     * <p>Note: 1210 Guinaifen (桂乃芬) 【吞火】, 1218 Jiaoqiu (椒丘) 【烬煨】 and 1410 Hysilens (海瑟音) 「风化/裂伤/灼烧/触电其中1种」
     * are the three. Each needs a REGENERATED row that names the state and its duration; until then each character's own
     * rule note registers what it would take.
     */
    @Test
    public void theSkillsThatNameAnInBattleStateAreTheRegisteredGap() {
        List<String> named = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> byCid : Constant.SKILLS.entrySet()) {
            int cid = byCid.getKey();
            for (int slot : byCid.getValue().keySet()) {
                SkillData data = SkillData.init(cid, slot);
                if (data == null || data.getEffect() != SkillEffectType.IMPAIR) {
                    continue;
                }
                // A technique (`attack_type: Maze`) describes the overworld, and this engine has no map layer: those
                // sentences name map states (目盲 / 魄散 / a field's 冻结) rather than an in-battle debuff.
                if ("Maze".equalsIgnoreCase(data.getSkillType())) {
                    continue;
                }
                String description = data.getDescription() == null ? "" : data.getDescription();
                if (IN_BATTLE_STATE.matcher(description).find()) {
                    named.add(cid + "/" + slot);
                }
            }
        }
        Assertions.assertEquals(List.of("1210/4", "1218/4", "1410/4"), named,
                "the Impair skills that name an in-battle state changed: each one is content work that needs its row, "
                        + "so a new entry must be a decision (register it in the character file's note)");
    }
}
