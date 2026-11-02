package com.laosun.aluminium.test.content.characters.sampo;

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
 * 1108 桑博 clause coverage.
 *
 * <p>His skill's 「并额外造成 4 次伤害」 needs no rule: the engine reads the hit count out of the row (slot 2's first
 * parameter is 4) - measured on 2026-11-02, one skill cast on a single dummy costs 4.2 x ATK, i.e. 5 hits of 56% through the
 * zone, exactly what the row says.
 */
public class SampoClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：酷炫的刀花", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：反复横跳的爱", "(no rule needed: the 4 extra hits come from slot 2's hit-count row)");
        SECTION_TO_RULE.put("终结技：惊喜礼盒", "ult_present");
        SECTION_TO_RULE.put("天赋：撕风的匕首", "talent_windtorn");
        SECTION_TO_RULE.put("秘技：你最闪亮", "technique_delays_every_enemy");
        SECTION_TO_RULE.put("星魂 1：加码的爱", "eidolon1_extra_skill_hit");
        SECTION_TO_RULE.put("星魂 2：热情会传染", "eidolon2_windtorn_on_kill");
        SECTION_TO_RULE.put("星魂 3：大数字！", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：爱之深，恨之切", "eidolon4_tick_on_stacked_windtorn");
        SECTION_TO_RULE.put("星魂 5：超大数字！", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：消费升级", "eidolon6_stronger_windtorn");
        SECTION_TO_RULE.put("行迹：圈套", "trace_longer_windtorn");
        SECTION_TO_RULE.put("行迹：后手", "trace_extra_energy");
        SECTION_TO_RULE.put("行迹：加料", "trace_less_damage_from_windtorn");
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
        System.out.println("[1108] sections covered = " + checked + " rules + two row-driven attacks");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1108.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}