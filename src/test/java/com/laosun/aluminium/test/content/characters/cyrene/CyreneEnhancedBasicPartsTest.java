package com.laosun.aluminium.test.content.characters.cyrene;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.data.SkillEffects;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.utils.JSONReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 1415 昔涟's enhanced basic 「向着爱与明天♪」 (141508) is <b>one row, two damage parts</b> - and this is the data side of
 * that fact.
 *
 * <p>The sentence is 「对指定敌方单体造成等同于昔涟 #3% 生命上限的冰伤，<b>随后</b>对敌方全体造成等同于昔涟 #1% 生命上限的
 * 冰伤」, so the row has a single-target part (<b>#3</b>, parameter index 2) followed by an area part (<b>#1</b>, index
 * 0) - the two are easy to read backwards because the description mentions #3 before #1. The row's own
 * {@code skill_effect} is {@code AoEAttack}, which is the shape only the <b>second</b> part has.
 *
 * <p>The count is not a hand-written opinion: the game's own {@code SkillMaxHitStatistic} says the enhanced basic is
 * <b>2</b> instances ({@code skill_max_hits.json}, {@code avatars.1415.Skill11 = 2}), and this guard fails if the table
 * and that column ever disagree.
 */
public class CyreneEnhancedBasicPartsTest {

    private static final int CYRENE = 1415;
    private static final int ENHANCED_BASIC_SLOT = 8;

    /**
     * The parts: order, shapes and the parameter each reads, agreed with the game's max-hit column.
     */
    @Test
    public void theRowStatesTheSinglePartFirstAndTheAreaPartSecond() {
        SkillEffectSpec spec = SkillEffects.forSkill(new DefaultSkill(CYRENE, ENHANCED_BASIC_SLOT, 1));
        Assertions.assertNotNull(spec, "skill_effects.json must describe 1415's slot 8");
        List<SkillEffectSpec.DamagePart> parts = spec.getDamageParts();
        Assertions.assertNotNull(parts, "a missing entry settles the row as one area hit and drops the single half");

        Map<String, Map<String, Map<String, Integer>>> table = JSONReader.fromJSON("skill_max_hits.json",
                new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {
                }.getType());
        Assertions.assertNotNull(table, "skill_max_hits.json must be generated (see README's generator section)");
        // Enhanced attacks are named by their own trigger key (Skill11 / Skill12), not by their data slot (8) --
        // measured in skills.json: 141508's SkillTriggerKey is "Skill11", and the statistic table follows it.
        Integer maxHits = table.getOrDefault("avatars", Map.of())
                .getOrDefault(String.valueOf(CYRENE), Map.of())
                .get("Skill11");
        Assertions.assertEquals(maxHits, parts.size(),
                "the table's part count must equal the game's own max-hit column (Skill11)");

        Assertions.assertEquals(2, parts.size(), "one single-target part + one area part");
        Assertions.assertEquals("SingleAttack", parts.get(0).getShape(), "「对指定敌方单体…」 comes first");
        Assertions.assertEquals(2, parts.get(0).getIndex(), "#3 (0-based index 2) is the single-target share");
        Assertions.assertEquals("AoEAttack", parts.get(1).getShape(), "「随后对敌方全体…」 comes second");
        Assertions.assertEquals(0, parts.get(1).getIndex(), "#1 (0-based index 0) is the area share");
    }
}
