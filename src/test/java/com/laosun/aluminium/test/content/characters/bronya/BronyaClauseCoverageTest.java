package com.laosun.aluminium.test.content.characters.bronya;

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
 * 1101 布洛妮娅 clause coverage.
 *
 * <p>Her Skill carries two sentences (the advance and the dispel) and the Ultimate three (ATK, CRIT DMG, and the duration),
 * so several sections map to more than one rule - which is exactly what a keyword scan cannot see.
 */
public class BronyaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：风隼", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：作战再部署（行动提前）", "skill_effect");
        SECTION_TO_RULE.put("战技：作战再部署（解除负面）", "skill_effect_2");
        SECTION_TO_RULE.put("终结技：攻击力提高", "ult_party_attack");
        SECTION_TO_RULE.put("终结技：暴击伤害提高", "ult_party_crit_damage");
        SECTION_TO_RULE.put("天赋：先人一步", "talent_advance_after_basic");
        SECTION_TO_RULE.put("秘技：攻击力提高 15%", "technique_party_attack");
        SECTION_TO_RULE.put("星魂 1：养精蓄锐", "eidolon1_skill");
        SECTION_TO_RULE.put("星魂 2：行动后速度提高", "eidolon2_speed_on_skill_target");
        SECTION_TO_RULE.put("星魂 3：终结技与天赋等级", "eidolon3_ultra_and_talent_levels");
        SECTION_TO_RULE.put("星魂 4：风弱普攻后追加攻击", "eidolon4_followup_on_wind_weak_basic");
        SECTION_TO_RULE.put("星魂 5：战技与普攻等级", "eidolon5_skill_and_common_levels");
        SECTION_TO_RULE.put("星魂 6：战技目标增伤", "eidolon6_skill");
        SECTION_TO_RULE.put("行迹：阵地", "battle_start");
        SECTION_TO_RULE.put("行迹：军势", "trace_military_power");
        SECTION_TO_RULE.put("行迹：号令", "trace_command_full_crit_on_basic");
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
        System.out.println("[1101] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1101.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}