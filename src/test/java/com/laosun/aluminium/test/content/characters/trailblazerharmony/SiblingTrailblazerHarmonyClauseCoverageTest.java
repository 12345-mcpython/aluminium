package com.laosun.aluminium.test.content.characters.trailblazerharmony;

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
 * 8006 开拓者（同谐）clause coverage - the sibling id of 8005, same kit, so the map mirrors it.
 *
 * <p>Two ids carry the same document; keeping the two maps identical is what makes the pair auditable: if one of them drifts,
 * the two tests disagree about which rule answers a section.
 */
public class SiblingTrailblazerHarmonyClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：摇摆的礼仪", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：中场馈赠的雨", "skill_extra_hits_random");
        SECTION_TO_RULE.put("终结技：喧嚣的舞灯巡游", "ult_dance");
        SECTION_TO_RULE.put("终结技：超击破", "on_damage_effect");
        SECTION_TO_RULE.put("天赋：全屏段的高空踏歌", "on_break_effect");
        SECTION_TO_RULE.put("秘技：即刻！独奏团", "technique_breaking_effect");
        SECTION_TO_RULE.put("星魂 1：您的最佳观众席", "eidolon1_skill_point_after_first_skill");
        SECTION_TO_RULE.put("星魂 2：越狱的跨洋彩虹", "eidolon2_energy_regen");
        SECTION_TO_RULE.put("星魂 3：休止符的疗养院", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：袒护白鸽的冠冕", "eidolon4_break_effect_for_others");
        SECTION_TO_RULE.put("星魂 5：包庇旧节拍的诗", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：明天，栖息聚光之下", "eidolon6_extra_skill_hits");
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
        System.out.println("[8006] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "8006.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}