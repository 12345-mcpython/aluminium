package com.laosun.aluminium.test.content.characters.huohuo;

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
 * 1217 藿藿 clause coverage.
 *
 * <p>Her Talent carries the state AND the per-turn trigger, and 星魂 1 adds two more pieces (the longer duration and the speed
 * aura), so those two sections map to several rules each.
 */
public class HuohuoClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：令旗•征风召雨", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：灵符•保命护身", "skill_talisman");
        SECTION_TO_RULE.put("终结技：尾巴•遣神役鬼", "ult_tail");
        SECTION_TO_RULE.put("天赋：凭附•气通天真（授予禳命）", "skill_grants_talisman");
        SECTION_TO_RULE.put("天赋：凭附•气通天真（每回合触发）", "turn_start_effect");
        SECTION_TO_RULE.put("秘技：凶煞•劾压鬼物", "technique_attack_down");
        SECTION_TO_RULE.put("星魂 1：禳命延长", "eidolon1_longer_talisman");
        SECTION_TO_RULE.put("星魂 1：全队速度提高", "eidolon1_speed_aura_on_start");
        SECTION_TO_RULE.put("星魂 2：判官书符", "e2_saves_an_ally_with_the_talisman");
        SECTION_TO_RULE.put("星魂 3：贞凶体质", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：坐卧不离", "eidolon4_more_healing_for_the_hurt");
        SECTION_TO_RULE.put("星魂 5：降妖捉鬼", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：同休共戚", "eidolon6_healed_ally_deals_more");
        SECTION_TO_RULE.put("行迹：不敢自专", "trace_fearful_to_act");
        SECTION_TO_RULE.put("行迹：贞凶之命", "trace_cursed_one");
        SECTION_TO_RULE.put("行迹：怯惧应激", "trace_stress_reaction");
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
        System.out.println("[1217] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1217.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}