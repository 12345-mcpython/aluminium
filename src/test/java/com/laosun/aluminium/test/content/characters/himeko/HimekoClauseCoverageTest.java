package com.laosun.aluminium.test.content.characters.himeko;

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
 * 1003 姬子 clause coverage: every section of her document maps to a rule (or to the skill row the engine reads).
 *
 * <p>Her talent is one section with four separate sentences (充能 on break, the follow-up at full charge, the starting
 * charge, and 星魂 2's extra), so the map names the rule that carries each of them rather than one rule per section.
 */
public class HimekoClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：相向而行", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：流火织成", "(no rule needed: the skill reads its own row; 行迹 灼热 adds its bonus)");
        SECTION_TO_RULE.put("终结技：留待残烬", "on_kill_effect");
        SECTION_TO_RULE.put("天赋：乘胜追击（击破获得充能）", "on_break_effect");
        SECTION_TO_RULE.put("天赋：乘胜追击（充能满发动追加攻击）", "attack_effect");
        SECTION_TO_RULE.put("天赋：乘胜追击（战斗开始时 1 点充能）", "battle_start");
        SECTION_TO_RULE.put("秘技：不完全燃烧", "technique_incomplete_combustion");
        SECTION_TO_RULE.put("星魂 1：童年", "eidolon1_speed_after_the_follow_up");
        SECTION_TO_RULE.put("星魂 2：邂逅", "starhunt_low_hp_damage");
        SECTION_TO_RULE.put("星魂 3：自我", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：坚守的心", "eidolon4_on_break");
        SECTION_TO_RULE.put("星魂 5：梦想", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：开拓！", "eidolon6_two_extra_random_hits");
        SECTION_TO_RULE.put("行迹：星火", "trace_starfire_burns_on_attack");
        SECTION_TO_RULE.put("行迹：灼热", "trace_scorching_skill_vs_burning");
        SECTION_TO_RULE.put("行迹：道标", "trace_beacon_crit_above_hp_line");
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
        System.out.println("[1003] sections covered = " + checked + " rules + two row-driven attacks");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1003.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}