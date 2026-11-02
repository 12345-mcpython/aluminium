package com.laosun.aluminium.test.content.characters.gepard;

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
 * 1104 杰帕德 clause coverage: every section AND every trace of his document maps to a rule.
 *
 * <p>His three traces are listed on one line of the document (「刚正 … 统领 … 战意 …」), which is why the coarse scan saw the word
 * 行迹 and stopped: 统领 had no rule at all until this map was written.
 */
public class GepardClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：一意之拳", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：震慑之击", "skill_daunting_smite");
        SECTION_TO_RULE.put("终结技：永屹之壁", "ult_enduring_bulwark");
        SECTION_TO_RULE.put("天赋：不屈之身", "trace_survives_a_lethal_hit_once");
        SECTION_TO_RULE.put("秘技：有情之证", "technique_opening_shield");
        SECTION_TO_RULE.put("星魂 1：恪尽职守", "eidolon1_freeze_chance");
        SECTION_TO_RULE.put("星魂 2：余寒", "eidolon2_slow_after_freeze_ends");
        SECTION_TO_RULE.put("星魂 3：永不陷落", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：精诚所至", "eidolon4_party_resistance");
        SECTION_TO_RULE.put("星魂 5：拳似寒铁", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：不屈的决意", "eidolon6_acts_and_heals_more");
        SECTION_TO_RULE.put("行迹：刚正", "trace_steadfast_aggro");
        SECTION_TO_RULE.put("行迹：统领", "trace_energy_to_full_when_the_talent_fires");
        SECTION_TO_RULE.put("行迹：战意", "trace_battle_will");
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
        System.out.println("[1104] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1104.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}