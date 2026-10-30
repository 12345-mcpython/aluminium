package com.laosun.aluminium.test.compare;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.utils.JSONReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The engine's hand-written {@code segments} must equal the <b>split ratios the game's own ability scripts state</b>, for
 * every row that has more than one segment of its primary target.
 *
 * <h2>Where the numbers come from</h2>
 * {@code skill_segment_ratios.json} is a digest of {@code E:/code/python/data/skill_segments.json}
 * ({@code export_skill_segments.py}), which walks the ability scripts in {@code E:/turnbasedgamedata} and reads each
 * {@code HitSplitRatio}. Its semantics are <b>multiplicative shares of the row's multiplier</b>
 * ({@code ModifyDamageDataBySplitRatio}), so the engine's {@code segments} array is exactly that list of ratios.
 *
 * <p><b>Why a digest and not the big file.</b> The full export is ~1.8&nbsp;MB of per-segment detail (formulas, opcodes,
 * branches); the engine needs one thing from it - the ratios of the primary target's default-branch segments - and only
 * for the rows that have more than one. That is 98 rows across 59 characters and 2 servants.
 *
 * <p><b>What it catches.</b> A four-instance row written as three settles the same total and rolls one crit too few; a row
 * written {@code [0.5, 0.5]} where the game says {@code [0.3, 0.7]} settles the right total with the wrong per-hit shape.
 * Neither has a symptom, which is why the ratios have to be compared against the scripts rather than against the totals.
 */
public class SkillSegmentRatiosTest {

    /** The four the reconciliation follows: their rows are engine-shipped, so a digest row with no engine entry is a bug. */
    private static final List<String> COVERED =
            List.of("1409", "1413", "1415", "1512", "11409", "11413", "11415", "11512");

    private static final double EPS = 1e-9;

    private static final TypeToken<Map<String, Object>> SHAPE = new TypeToken<>() {
    };

    /**
     * Every digest row the engine claims must state the same ratios, in the same order, at the same count.
     */
    @Test
    public void everyShippedSegmentListMatchesTheScripts() {
        Map<String, Object> digest = JSONReader.fromJSON("skill_segment_ratios.json", SHAPE.getType());
        Map<String, Object> effects = JSONReader.fromJSON("skill_effects.json",
                new TypeToken<Map<String, Object>>() {
                }.getType());
        Assertions.assertNotNull(digest, "skill_segment_ratios.json must be generated (see its own _meta)");
        Assertions.assertNotNull(effects, "skill_effects.json must be generated");

        List<String> problems = new ArrayList<>();
        TreeSet<String> checked = new TreeSet<>();
        for (Map.Entry<String, Object> character : digest.entrySet()) {
            String cid = character.getKey();
            if (cid.startsWith("_")) {
                continue;
            }
            Map<String, Object> slots = asMap(character.getValue());
            for (Map.Entry<String, Object> slot : slots.entrySet()) {
                Map<String, Object> row = asMap(slot.getValue());
                List<Object> ratios = asList(row.get("ratios"));
                int hits = ((Number) row.get("hit_count")).intValue();
                String where = cid + "/" + slot.getKey() + " (" + row.get("phase") + ")";
                checked.add(where);

                List<Double> shipped = shippedSegments(effects, cid, slot.getKey());
                if (COVERED.contains(cid)) {
                    if (shipped == null) {
                        problems.add(where + ": the scripts state " + ratios + " but the engine states no segments");
                        continue;
                    }
                    if (shipped.size() != ratios.size()) {
                        problems.add(where + ": " + shipped.size() + " segments shipped, the scripts state "
                                + ratios.size() + " " + ratios);
                        continue;
                    }
                    for (int i = 0; i < ratios.size(); i++) {
                        double expected = ((Number) ratios.get(i)).doubleValue();
                        if (Math.abs(shipped.get(i) - expected) > EPS) {
                            problems.add(where + ": segment " + (i + 1) + " is " + shipped.get(i)
                                    + ", the scripts state " + expected);
                        }
                    }
                    if (shipped.size() != hits) {
                        problems.add(where + ": " + shipped.size() + " segments, the scripts' single-target hit count is "
                                + hits);
                    }
                } else if (shipped != null && shipped.size() != ratios.size()) {
                    problems.add(where + ": " + shipped.size() + " segments shipped, the scripts state " + ratios.size());
                }
            }
        }
        Assertions.assertTrue(problems.isEmpty(), "segment ratios disagree with the ability scripts:\n  "
                + String.join("\n  ", problems));
        System.out.println("[segments] checked " + checked.size() + " multi-segment rows against the scripts");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    /**
     * The engine's {@code segments} for one row, read from {@code skill_effects.json} the way the loader reads it.
     *
     * @param effects the parsed table
     * @param cid     the character or servant id
     * @param slot    the data slot
     * @return the ratios, or {@code null} when the row states none
     */
    private static List<Double> shippedSegments(Map<String, Object> effects, String cid, String slot) {
        Map<String, Object> forCid = asMap(effects.get(cid));
        if (forCid == null) {
            return null;
        }
        Map<String, Object> entry = asMap(forCid.get(slot));
        if (entry == null || !entry.containsKey("segments")) {
            return null;
        }
        List<Double> ratios = new ArrayList<>();
        for (Object each : asList(entry.get("segments"))) {
            ratios.add(((Number) each).doubleValue());
        }
        return ratios;
    }
}
