package com.laosun.aluminium.test.content.characters.sushang;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1206 素裳 clause coverage: every section of {@code 1206_素裳.md}, plus the two readings that had never been pinned.
 *
 * <p><b>Why this file exists.</b> The ledger had already recorded every clause as done ("仍登记：无") while the character file's
 * {@code _gaps} still said 「见 CONTENT-STATUS.md 的逐角色登记」 — a pointer, not a gap. This class is the audit that closes it:
 * one line per section, and the two Eidolon readings that no other test covered.
 */
public class SushangClauseCoverageTest {

    private static final int SUSHANG = 1206;
    private static final int ALLY = 1002;

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：云骑剑经 • 素霓", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：云骑剑经 • 山倾", "skill_sword_stance_chance");
        SECTION_TO_RULE.put("终结技：太虚形蕴 • 烛夜", "ult_acts_again_immediately");
        SECTION_TO_RULE.put("天赋：游刃若水", "talent_speed_on_break");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：云骑剑经 • 叩阵", "technique_opening_damage");
        SECTION_TO_RULE.put("行迹：赤子", "trace_low_hp_aggro_down");
        SECTION_TO_RULE.put("行迹：逐寇", "trace_sword_stance_damage_per_stack");
        SECTION_TO_RULE.put("行迹：破敌", "trace_breaking_advance_after_common");
        SECTION_TO_RULE.put("行迹：击破特攻 / 攻击 / 效果抵抗（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：游刃有余", "eidolon1_skill_point_on_a_broken_target");
        SECTION_TO_RULE.put("星魂 2：其身百炼", "eidolon2_damage_reduction_after_sword_stance");
        SECTION_TO_RULE.put("星魂 3：传古剑流", "eidolon3_ultra_and_talent_levels");
        SECTION_TO_RULE.put("星魂 4：其心百辟", "eidolon4_breaking_effect");
        SECTION_TO_RULE.put("星魂 5：太虚神意", "eidolon5_skill_and_common_levels");
        SECTION_TO_RULE.put("星魂 6：上善若水", "eidolon6_talent_speed_stacks_twice");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        int checked = 0;
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            if (entry.getValue().startsWith("(")) {
                continue;
            }
            checked++;
            Assertions.assertTrue(has(rules, entry.getValue()),
                    entry.getKey() + " has no rule (expected " + entry.getValue() + ")");
        }
        System.out.println("[1206] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 星魂 4: 「素裳的击破特攻提高 40%」. Measured: BREAKING_EFFECT <b>0</b> at E0, <b>0.4</b> at E4.
     */
    @Test
    public void eidolon4RaisesTheBreakingEffect() {
        Assertions.assertEquals(0.0, breakingEffectOf(0), 1e-9, "control: nothing at E0");
        Assertions.assertEquals(0.4, breakingEffectOf(4), 1e-9, "「击破特攻提高 40%」");
    }

    /**
     * 星魂 6: 「天赋的加速效果可以叠加，最多叠加2层」 — the same two breaks move the speed at E6 and only refresh at E0.
     *
     * <p>Measured over two breaks in one battle: E0 <b>107 → 128.4 → 128.4</b> (the second refreshes); E6
     * <b>129.47 → 151.94 → 174.41</b> (it stacks). The point pinned here is the <b>difference between the two Eidolons</b>, not
     * the three numbers: one stack versus two.
     */
    @Test
    public void eidolon6LetsTheSpeedStackTwice() {
        double[] e0 = speedsAfterTwoBreaks(0);
        Assertions.assertEquals(e0[1], e0[2], 1e-6, "E0: 「最多叠加2层」 does not apply — the second break only refreshes");

        double[] e6 = speedsAfterTwoBreaks(6);
        Assertions.assertTrue(e6[2] > e6[1], "E6: the second break stacks (" + e6[1] + " -> " + e6[2] + ")");
        Assertions.assertEquals(e6[1] + (e6[1] - e6[0]), e6[2], 1e-6,
                "and it stacks by the SAME amount as the first one");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double breakingEffectOf(int eidolon) {
        return fixture(eidolon).sushang.getAttribute(AttributeType.BREAKING_EFFECT).get();
    }

    /** Speeds at [before any break, after one, after two]. */
    private static double[] speedsAfterTwoBreaks(int eidolon) {
        Fixture f = fixture(eidolon);
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double one = f.sushang.getAttribute(AttributeType.SPEED).get();
        f.breakOnce();
        double two = f.sushang.getAttribute(AttributeType.SPEED).get();
        return new double[]{before, one, two};
    }

    private static Fixture fixture(int eidolon) {
        return new Fixture(eidolon);
    }

    private static boolean has(JsonArray rules, String id) {
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1206.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static final class Fixture {
        private final Character sushang;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            sushang = CharacterFactory.create(SUSHANG, 80, false, null, null, eidolon);
            battle = new Battle(List.of(sushang, CharacterFactory.create(ALLY, 80)), List.of(enemy),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void breakOnce() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, sushang, enemy, 0, 0));
            battle.processRequests();
        }
    }
}
