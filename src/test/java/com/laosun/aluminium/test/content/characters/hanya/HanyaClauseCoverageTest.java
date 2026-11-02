package com.laosun.aluminium.test.content.characters.hanya;

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
 * 1215 寒鸦 clause coverage.
 *
 * <p>Her Skill and Talent each name three attack types (「2次普攻、战技、终结技后」), so those two sections map to four and three
 * rules respectively - the split is the whole reason a keyword scan cannot judge this file.
 */
public class HanyaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：冥谶天笔", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：生灭系缚（承负）", "skill_effect");
        SECTION_TO_RULE.put("战技：触发计数（普攻）", "attack_effect");
        SECTION_TO_RULE.put("战技：触发计数（战技）", "attack_effect_2");
        SECTION_TO_RULE.put("战技：触发计数（终结技）", "attack_effect_3");
        SECTION_TO_RULE.put("战技：回战技点与自动解除", "attack_effect_4");
        SECTION_TO_RULE.put("终结技：十王敕令", "ult_blessing");
        SECTION_TO_RULE.put("天赋：罚恶（普攻）", "talent_common");
        SECTION_TO_RULE.put("天赋：罚恶（战技）", "talent_skill");
        SECTION_TO_RULE.put("天赋：罚恶（终结技）", "talent_ultra");
        SECTION_TO_RULE.put("秘技：判冥", "technique_prey_on_a_random_enemy");
        SECTION_TO_RULE.put("星魂 1：一心", "eidolon1_on_kill");
        SECTION_TO_RULE.put("星魂 2：二观", "eidolon2_skill");
        SECTION_TO_RULE.put("星魂 3：三尘", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：四谛", "eidolon4_duration");
        SECTION_TO_RULE.put("星魂 5：五阴", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：六正", "eidolon6_talent");
        SECTION_TO_RULE.put("行迹：幽府", "on_kill_effect");
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
        System.out.println("[1215] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1215.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}