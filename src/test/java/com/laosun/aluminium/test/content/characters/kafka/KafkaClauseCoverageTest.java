package com.laosun.aluminium.test.content.characters.kafka;

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
 * 1005 卡芙卡 clause coverage.
 *
 * <p>Written after the coarse section scan let a whole section through: guard 13 matches the WORD 「秘技」, and her Eidolon 6
 * quotes 「终结技、秘技与天赋触发的追加攻击」, so the technique counted as covered while no technique rule existed. The
 * hand-written map is what caught it - and the technique is now a rule.
 */
public class KafkaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：夜间独白", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：月光摩挲连绵", "skill_dot_ticks_now");
        SECTION_TO_RULE.put("终结技：悲剧尽头的颤音", "ult_shock_and_tick");
        SECTION_TO_RULE.put("天赋：温柔亦同残酷", "talent_followup");
        SECTION_TO_RULE.put("秘技：宽恕无关慈悲", "technique_shock_on_every_enemy");
        SECTION_TO_RULE.put("星魂 1：无穷动！无穷", "eidolon1_dot_taken_on_followup");
        SECTION_TO_RULE.put("星魂 2：狂想者，呜咽", "eidolon2_party_dot_damage");
        SECTION_TO_RULE.put("星魂 3：我赞美，即兴", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：把宣叙呈献给", "eidolon4_energy_on_her_shock_tick");
        SECTION_TO_RULE.put("星魂 5：只有今晚奏鸣", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：回旋，悄悄地", "eidolon6_shock_multiplier");
        SECTION_TO_RULE.put("行迹：触电持续 +1 回合", "trace_shock_lasts_one_more_turn");
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
        System.out.println("[1005] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1005.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}