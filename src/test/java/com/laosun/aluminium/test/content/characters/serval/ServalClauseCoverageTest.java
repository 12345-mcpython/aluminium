package com.laosun.aluminium.test.content.characters.serval;

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
 * 1103 希露瓦 clause coverage: every section of her document has a rule that names it.
 *
 * <p>Built from the document's own section list (普攻 / 战技 / 终结技 / 天赋 / 秘技 / 星魂 1-6 / 行迹), so "fully implemented"
 * means <b>every section is represented</b>, not merely "no gaps registered". The basic attack needs no rule of its own -
 * its numbers live in the skill row the engine reads - which is noted in the expected map.
 */
public class ServalClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：雷鸣音阶", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：电光石火间", "skill_shock");
        SECTION_TO_RULE.put("终结技：机械热潮登场！", "ult_effect");
        SECTION_TO_RULE.put("天赋：燃情和弦", "attack_effect");
        SECTION_TO_RULE.put("秘技：晚安，贝洛伯格", "technique_shock_and_damage");
        SECTION_TO_RULE.put("星魂 1：余音绕梁", "eidolon1_adjacent_damage_on_basic");
        SECTION_TO_RULE.put("星魂 2：安可！", "eidolon2_energy_on_talent_additional_damage");
        SECTION_TO_RULE.put("星魂 3：听，齿轮的心跳", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：制造噪音吧！", "eidolon4_ult");
        SECTION_TO_RULE.put("星魂 5：贝洛伯格最强音！", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：这一曲，贯穿天穹！", "eidolon6_on_damage");
        SECTION_TO_RULE.put("行迹：电音", "battle_start");
        SECTION_TO_RULE.put("行迹：狂热", "on_kill_effect");
        SECTION_TO_RULE.put("行迹：摇滚", "battle_start_2");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            String rule = entry.getValue();
            if (rule.startsWith("(")) {
                continue;   // the basic attack reads the skill row; there is no rule to look for
            }
            boolean found = false;
            for (var element : rules) {
                JsonObject candidate = element.getAsJsonObject();
                if (candidate.has("id") && rule.equals(candidate.get("id").getAsString())) {
                    found = true;
                    break;
                }
            }
            Assertions.assertTrue(found, entry.getKey() + " has no rule (expected " + rule + ")");
        }
        System.out.println("[1103] sections covered = " + (SECTION_TO_RULE.size() - 1) + " rules + the basic attack's row");
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1103.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}