package com.laosun.aluminium.test.content.characters.asta;

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
 * 1009 艾丝妲 clause coverage. Every document section maps to a rule, except Eidolon 4, which is a REGISTERED GAP (see
 * {@code _gaps}): 「天赋的蓄能层数大于等于 2 时，艾丝妲的能量恢复效率提高 15%」 is a condition that must turn on and off as a
 * live counter crosses 2, and the axis has no "while" modifier.
 */
public class AstaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：光谱射线", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：流星群落", "skill_extra_hits_random");
        SECTION_TO_RULE.put("终结技：星空祝言", "ult_flat_speed_for_all_allies");
        SECTION_TO_RULE.put("天赋：蓄能获取", "talent_charge_per_distinct_target");
        SECTION_TO_RULE.put("天赋：火弱点额外一层", "talent_charge_on_fire_weakness");
        SECTION_TO_RULE.put("天赋：每层加攻", "talent_charge_raises_all_attack");
        SECTION_TO_RULE.put("天赋：第 2 回合起每回合 -3 层", "talent_charge_decays_from_second_turn");
        SECTION_TO_RULE.put("秘技：灵光一现", "technique_opening_aoe");
        SECTION_TO_RULE.put("星魂 1：星有无言之歌", "eidolon1_extra_skill_hit");
        SECTION_TO_RULE.put("星魂 2：月见圆缺之意", "eidolon2_ult_stops_the_decay_once");
        SECTION_TO_RULE.put("星魂 3：黄道陨石之变", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 5：深空天体之谜", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：眠于银河之下", "eidolon6_charge_decay_reduced");
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
        System.out.println("[1009] sections covered = " + checked + " rules + the basic attack's row (Eidolon 4 is a gap)");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1009.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}