package com.laosun.aluminium.test.compare;


import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.data.SkillEffects;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.utils.JSONReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A skill's damage <b>segment count</b> is data: {@code skill_max_hits.json} carries the game's own
 * 「官方最大命中次数」 per skill slot, so the hand-written {@code segments} in {@code skill_effects.json} must agree
 * with it.
 *
 * <h2>Why this guard matters</h2>
 * The split RATIOS can only come from the skill's own segment table (the document states them one by one), so the
 * {@code segments} array stays hand-written. Its <b>length</b>, however, is exactly the max-hit column - and a
 * hand-written array can disagree with it in the quietest possible way: a four-instance skill written as three settles
 * the same total damage, but three crit rolls instead of four.
 *
 * <p>The table arrived with the 2026-10 data migration ({@code skill_max_hits.json}, generated from
 * {@code AvatarSkillConfig.SkillMaxHit}), and 1002's own rows are {@code Skill01: 2, Skill02: 4, Skill03: 1} - the same
 * numbers the segments were derived from before the table existed.
 */
public class SegmentCountAgreementTest {

    /**
     * For every skill that states {@code segments}, its segment count equals the data's max-hit column.
     */
    @Test
    public void everyHandWrittenSegmentCountMatchesTheData() {
        Map<String, Map<String, Map<String, Integer>>> table = JSONReader.fromJSON("skill_max_hits.json",
                new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {
                }.getType());
        Assertions.assertNotNull(table, "skill_max_hits.json must be generated (see README's generator section)");

        List<String> problems = new ArrayList<>();
        TreeSet<String> checked = new TreeSet<>();
        for (int cid : new int[]{1002}) {
            for (int slot = 1; slot <= 7; slot++) {
                var spec = SkillEffects.forSkill(new DefaultSkill(cid, slot, 1));
                List<Double> segments = spec == null ? null : spec.getSegments();
                if (segments == null || segments.isEmpty()) {
                    continue;
                }
                Integer maxHits = table.getOrDefault("avatars", Map.of())
                        .getOrDefault(String.valueOf(cid), Map.of())
                        .get("Skill" + String.format("%02d", slot));
                if (maxHits == null) {
                    problems.add(cid + "/" + slot + ": segments stated but the data has no max-hit row");
                    continue;
                }
                checked.add(cid + "/" + slot + "=" + maxHits);
                if (maxHits != segments.size()) {
                    problems.add(cid + "/" + slot + ": " + segments.size() + " segments, data says " + maxHits);
                }
            }
        }
        Assertions.assertFalse(checked.isEmpty(),
                "precondition: at least one skill states segments (1002's basic / Skill do)");
        Assertions.assertEquals(List.of(), problems,
                "hand-written segment counts disagree with skill_max_hits.json: " + problems);
    }

    /**
     * The same agreement for a <b>servant's</b> hand-written segments.
     *
     * <p>The max-hit column for a memosprite is keyed by the ability CONFIG name
     * ({@code Servant_EvernightServant_00_Config}), not by the servant id ({@code 11413}) - that is how
     * {@code skill_max_hits.json} is generated - so the two spellings are bridged here explicitly rather than guessed.
     *
     * <p>Reader: 11413 「长夜」's skill 1, whose six instances are the six {@code DamageByAttackProperty} tasks of
     * {@code Servant_EvernightServant_00_Skill01_Phase02} (HitSplitRatio 0.1 x 5 + 0.5). The capture carries the same
     * cast as 177.60 x 5 + 888.01 x 1; it looks like five because {@code capture-flow} groups by unmodified base.
     */
    @Test
    public void everyServantSegmentCountMatchesTheData() {
        Map<String, Map<String, Map<String, Integer>>> table = JSONReader.fromJSON("skill_max_hits.json",
                new TypeToken<Map<String, Map<String, Map<String, Integer>>>>() {
                }.getType());
        Assertions.assertNotNull(table, "skill_max_hits.json must be generated (see README's generator section)");
        // servant id -> the ability config the max-hit section is keyed by.
        Map<Integer, String> servants = Map.of(11413, "Servant_EvernightServant_00_Config");

        List<String> problems = new ArrayList<>();
        TreeSet<String> checked = new TreeSet<>();
        for (Map.Entry<Integer, String> servant : servants.entrySet()) {
            for (int slot = 1; slot <= 7; slot++) {
                var spec = SkillEffects.forSkill(new DefaultSkill(servant.getKey(), slot, 10));
                List<Double> segments = spec == null ? null : spec.getSegments();
                if (segments == null || segments.isEmpty()) {
                    continue;
                }
                Integer maxHits = table.getOrDefault("servants", Map.of())
                        .getOrDefault(servant.getValue(), Map.of())
                        .get("Skill" + String.format("%02d", slot));
                if (maxHits == null) {
                    problems.add(servant.getKey() + "/" + slot + ": segments stated but the data has no max-hit row");
                    continue;
                }
                checked.add(servant.getKey() + "/" + slot + "=" + maxHits);
                if (maxHits != segments.size()) {
                    problems.add(servant.getKey() + "/" + slot + ": " + segments.size() + " segments, data says " + maxHits);
                }
                double sum = segments.stream().mapToDouble(Double::doubleValue).sum();
                if (Math.abs(sum - 1.0) > 1e-9) {
                    problems.add(servant.getKey() + "/" + slot + ": the split fractions sum to " + sum + ", not 1");
                }
            }
        }
        Assertions.assertFalse(checked.isEmpty(),
                "precondition: at least one servant skill states segments (11413's skill 1 does)");
        Assertions.assertEquals(List.of(), problems,
                "servant segment counts disagree with skill_max_hits.json: " + problems);
    }
}
