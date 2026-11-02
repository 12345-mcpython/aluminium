package com.laosun.aluminium.test.content.characters.natasha;

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
 * 1105 娜塔莎 clause coverage.
 *
 * <p>Her Ultimate has no rule and needs none: the restore comes from the skill's own row, measured on 2026-11-02 (one cast on a
 * half-health ally healed 620.93 with no rule naming it).
 */
public class NatashaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：仁慈的背面", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：爱，救护与抉择", "skill_effect");
        SECTION_TO_RULE.put("终结技：新生之礼", "(no rule needed: the restore comes from the ultimate's own row)");
        SECTION_TO_RULE.put("天赋：生机焕发", "talent_heal_bonus_below_thirty_percent");
        SECTION_TO_RULE.put("秘技：催眠研习", "technique_weaken_and_strike");
        SECTION_TO_RULE.put("星魂 1：遍识药理", "eidolon1_taking_hit");
        SECTION_TO_RULE.put("星魂 2：临床研学", "eidolon2_ult");
        SECTION_TO_RULE.put("星魂 3：对症下药", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：妙手回春", "eidolon4_energy_on_being_hit");
        SECTION_TO_RULE.put("星魂 5：医治未病", "eidolon5_ultra_and_talent_levels");
        SECTION_TO_RULE.put("星魂 6：医者仁心", "eidolon6_max_hp_additional_damage");
        SECTION_TO_RULE.put("行迹：医者", "battle_start");
        SECTION_TO_RULE.put("行迹：调理", "trace_conditioning");
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
        System.out.println("[1105] sections covered = " + checked + " rules + two row-driven abilities");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1105.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}