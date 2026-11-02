package com.laosun.aluminium.test.content.characters.march7th;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1001 三月七 clause coverage: every section of her document maps to a rule (or to the skill row the engine reads).
 *
 * <p>Her file had no {@code _gaps} array at all, which the "fully implemented" count read as complete without ever checking
 * that each section was represented. This is that check.
 */
public class March7thClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：极寒的弓矢", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：护盾", "skill_shield");
        SECTION_TO_RULE.put("战技：大于等于 30% 生命时被攻击概率提高", "skill_soft_aggro");
        SECTION_TO_RULE.put("终结技：冰刻箭雨之时", "ultimate_freeze");
        SECTION_TO_RULE.put("天赋：少女的特权（反击）", "counter");
        SECTION_TO_RULE.put("秘技：冻人的瞬间", "technique_freeze");
        SECTION_TO_RULE.put("星魂 1：记忆中的你", "eidolon_one_gives_energy_per_frozen_target");
        SECTION_TO_RULE.put("星魂 2：记忆中的它", "eidolon_two_shields_the_lowest_hp_ally_at_battle_start");
        SECTION_TO_RULE.put("星魂 3：记忆中的一切", "eidolon_three_raises_the_ult_and_basic");
        SECTION_TO_RULE.put("星魂 4：反击次数 +1", "eidolon_four_allows_one_more_counter");
        SECTION_TO_RULE.put("星魂 4：反击伤害按防御力提高", "eidolon_four_adds_defence_scaled_counter_damage");
        SECTION_TO_RULE.put("星魂 5：不想再忘却", "eidolon_five_raises_the_skill_and_talent");
        SECTION_TO_RULE.put("星魂 6：就这样，一直…", "eidolon_six_heals_shielded_allies_each_turn");
        SECTION_TO_RULE.put("行迹：纯洁", "skill_cleanses_one_debuff");
        SECTION_TO_RULE.put("行迹：加护", "trace_grace_adds_one_turn_to_the_shield");
        SECTION_TO_RULE.put("行迹：冰咒", "trace_ice_curse_raises_the_freeze_chance");
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
        System.out.println("[1001] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1001.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}