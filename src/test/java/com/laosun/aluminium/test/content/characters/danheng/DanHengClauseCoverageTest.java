package com.laosun.aluminium.test.content.characters.danheng;

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
 * 1002 丹恒 clause coverage: every section of his document maps to a rule (or to the skill row the engine reads).
 *
 * <p>His file had the rules but no such list, so "fully implemented" was still only a claim about the gap array.
 */
public class DanHengClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：云骑枪术•朔风", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：云骑枪术•疾雨", "skill_crit_slows_the_target");
        SECTION_TO_RULE.put("终结技：洞天幻化，长梦一觉", "ult_ethereal_dream_hits_a_slowed_target");
        SECTION_TO_RULE.put("天赋：寸长寸强", "talent_wind_pen");
        SECTION_TO_RULE.put("秘技：破敌锋芒", "technique_opening_attack");
        SECTION_TO_RULE.put("星魂 1：穷高极天，亢盈难久", "eidolon1_crit_chance_against_a_healthy_target");
        SECTION_TO_RULE.put("星魂 2：威制八毒，灭却炎烟", "eidolon2_shortens_the_talent_cooldown");
        SECTION_TO_RULE.put("星魂 3：幽明变化，自在蟠跃", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：奋迅三昧，如日空居", "eidolon4_ult_kill_acts_again");
        SECTION_TO_RULE.put("星魂 5：一渧天水，六虚洪流", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：须绳缚身，沉潜勿用", "eidolon6_deepens_the_slow");
        SECTION_TO_RULE.put("行迹：潜龙", "trace_low_hp_aggro_down");
        SECTION_TO_RULE.put("行迹：绝影", "trace_fleeting_shadow_speed");
        SECTION_TO_RULE.put("行迹：罡风", "trace_gang_feng_hits_a_slowed_target");
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
        System.out.println("[1002] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1002.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}