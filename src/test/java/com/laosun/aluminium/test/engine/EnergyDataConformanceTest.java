package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Constant;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The energy a cast restores comes out of the skill's <b>own row</b> ({@code sp_base}), not out of the conventional
 * constants - and the two agree everywhere except three registered rows.
 *
 * <h2>Why this is a guard</h2>
 * The provider used to answer the constants (20 / 30 / 5) directly, and its javadoc said so on purpose: the data's
 * {@code sp_base} is a <b>per-hit</b> value for a Bounce skill (Asta 6, Welt 10) and the multiply needs the ability
 * config's {@code SPHitRatio}, which this project's data does not carry. The instance count is the same number without
 * that table - so the provider now reads the row and multiplies, and this class checks that the answers still <b>are</b>
 * the conventional tiers wherever the game says so. A regression that went back to the constants would leave the first
 * case green on a matching row and red on 1502 Yao Guang (曜青)'s off-tier basic attack, which is exactly what the old
 * expectation in {@code SpecialEnergyProviderTest} used to hold still.
 *
 * <h2>What is checked</h2>
 * <ol>
 *   <li>every cast's amount equals {@code sp_base} x the instances it delivers (a Bounce's count, read here from the
 *       raw description independently of the engine's own reader; an ultimate is a total, so no multiply);</li>
 *   <li>the rows whose total is <b>not</b> the conventional tier are exactly the registered three - a data update that
 *       adds a fourth, or an instance count that drifts, fails here;</li>
 *   <li>the two Bounce anchors that make the multiply observable (Asta 6 x 5 = 30, Welt 10 x 3 = 30).</li>
 * </ol>
 */
public class EnergyDataConformanceTest {
    private static final double EPS = 1e-9;

    /**
     * 「额外造成 N 次伤害」 - the prose a Bounce uses to state its extra hits. Read independently of
     * {@code SkillData.bounceAdditionalHits}, so agreement between the two is information rather than a tautology.
     */
    private static final Pattern PROSE_EXTRA_HITS = Pattern.compile("额外造成\\s*(\\d+)\\s*次");
    private static final Pattern MARKUP = Pattern.compile("<[^>]*>|\\\\n");

    /**
     * The conventional tier of each castable slot: basic 20 / skill 30 / ultimate 5.
     */
    private static final Map<Integer, Double> TIER = Map.of(1, 20.0, 2, 30.0, 3, 5.0);

    private static final StandardEnergyProvider PROVIDER = new StandardEnergyProvider();

    /**
     * Part 1: the engine's answer is the row's own number times the instances, for every shipped castable skill.
     */
    @Test
    public void everyCastRestoresItsOwnRowsSpBase() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> byCid : Constant.SKILLS.entrySet()) {
            int cid = byCid.getKey();
            for (Map.Entry<Integer, com.laosun.aluminium.beans.Skill> bySlot : byCid.getValue().entrySet()) {
                int slot = bySlot.getKey();
                if (!TIER.containsKey(slot)) {
                    continue;
                }
                SkillData data = SkillData.init(cid, slot);
                if (data == null || !data.isLoaded() || data.getSpBase() == null) {
                    continue;
                }
                // An ultimate goes through its own hook; the other two slots only gain energy when the row's own
                // category says it is a basic attack / skill (a summoning row in slot 2 gains none).
                boolean ultra = slot == 3;
                if (!ultra && data.getCategory() != com.laosun.aluminium.enums.SkillCategory.NORMAL
                        && data.getCategory() != com.laosun.aluminium.enums.SkillCategory.BPSKILL) {
                    continue;
                }
                double expected = ultra ? data.getSpBase()
                        : data.getSpBase() * instancesOf(data, bySlot.getValue().maxLevel());
                Character user = Character.builder().cid(cid).level(80).build();
                // A hand-built skill is what a cast uses; the user's own map has no such entry, so the level is the
                // skill's own - the same resolver, nothing special-cased.
                Skill cast = new DefaultSkill(cid, slot, bySlot.getValue().maxLevel());
                double actual = ultra ? PROVIDER.onUltCast(user, cast).amount()
                        : PROVIDER.onSkillCast(user, cast, Set.of()).amount();
                checked++;
                if (Math.abs(actual - expected) > 1e-6) {
                    problems.add(cid + "/" + slot + " expected " + expected + " got " + actual);
                }
            }
        }
        Assertions.assertTrue(checked > 200, "expected the shipped skill table, checked " + checked + " skills");
        Assertions.assertEquals(List.of(), problems,
                "these casts do not restore their row's own sp_base: " + problems);
    }

    /**
     * Part 2: the rows that are <b>not</b> the conventional tier, as a closed set.
     *
     * <p>Note: The three are data facts, not engine choices - 1502 Yao Guang (曜青)'s basic attack states 30 (a stronger
     * basic), and 1212 / 1402 have a skill row that states 20 where the tier says 30. Any change here must be a decision.
     */
    @Test
    public void theOffTierRowsAreExactlyTheRegisteredThree() {
        TreeSet<String> offTier = new TreeSet<>();
        for (Map.Entry<Integer, Map<Integer, com.laosun.aluminium.beans.Skill>> byCid : Constant.SKILLS.entrySet()) {
            for (Map.Entry<Integer, com.laosun.aluminium.beans.Skill> bySlot : byCid.getValue().entrySet()) {
                int slot = bySlot.getKey();
                if (!TIER.containsKey(slot)) {
                    continue;
                }
                SkillData data = SkillData.init(byCid.getKey(), slot);
                if (data == null || !data.isLoaded() || data.getSpBase() == null) {
                    continue;
                }
                // A SERVANT (忆灵) is not a character: its basics carry their own energy values (measured: 11402 -> 10,
                // 11409 -> 5, 18007 -> 10), and the 20/30/5 tiers describe the avatar table.
                if (!Constant.CHARACTERS.containsKey(byCid.getKey())) {
                    continue;
                }
                double total = slot == 3 ? data.getSpBase()
                        : data.getSpBase() * instancesOf(data, bySlot.getValue().maxLevel());
                if (Math.abs(total - TIER.get(slot)) > EPS) {
                    offTier.add(byCid.getKey() + "/" + slot + "=" + total);
                }
            }
        }
        Assertions.assertEquals(new TreeSet<>(List.of("1212/2=20.0", "1402/2=20.0", "1502/1=30.0")), offTier,
                "the off-tier rows changed: each one is a deliberate data fact, so adding or losing one must be a decision");
    }

    /**
     * Part 3: the two anchors that make the per-hit multiply observable - both totals are the conventional 30.
     */
    @Test
    public void aBounceMultipliesItsPerHitRowByTheInstancesItDelivers() {
        Assertions.assertEquals(30, energyOf(1009, 2), 1e-6,
                "1009 Asta (艾丝妲): 6 per hit x 5 hits (1 + 「额外造成4次伤害」)");
        Assertions.assertEquals(30, energyOf(1004, 2), 1e-6,
                "1004 Welt (瓦尔特): 10 per hit x 3 hits (1 + 「额外造成2次伤害」)");
    }

    /**
     * The energy one cast of that slot restores, through the provider.
     */
    private static double energyOf(int cid, int slot) {
        Character user = Character.builder().cid(cid).level(80).build();
        return PROVIDER.onSkillCast(user, new DefaultSkill(cid, slot, 1), Set.of()).amount();
    }

    /**
     * How many damage instances one cast of this skill settles, read from the data the way the game states it: a
     * Bounce's prose count first, then the row's own integer; everything else settles one instance as far as energy goes.
     */
    private static int instancesOf(SkillData data, int level) {
        if (data.getEffect() != com.laosun.aluminium.enums.SkillEffectType.BOUNCE) {
            return 1;
        }
        String description = MARKUP.matcher(data.getDescription() == null ? "" : data.getDescription()).replaceAll("");
        java.util.regex.Matcher prose = PROSE_EXTRA_HITS.matcher(description);
        if (prose.find()) {
            return 1 + Integer.parseInt(prose.group(1));
        }
        var rows = data.getSkills();
        if (rows.isEmpty()) {
            return 1;
        }
        int row = Math.max(1, Math.min(level, rows.size())) - 1;
        for (double value : rows.get(row)) {
            if (value >= 1 && value == Math.rint(value)) {
                return 1 + (int) value;
            }
        }
        return 1;
    }
}
