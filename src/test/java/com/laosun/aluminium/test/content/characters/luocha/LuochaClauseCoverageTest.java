package com.laosun.aluminium.test.content.characters.luocha;

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

/** 1203 罗刹 clause coverage: every section and every trace maps to a rule. */
public class LuochaClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：黑渊的棘刺", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：白花的祈望（治疗与花）", "skill_abyss_flower");
        SECTION_TO_RULE.put("战技：白花的祈望（自动触发）", "skill_trigger_on_crossing");
        SECTION_TO_RULE.put("终结技：归葬的遂愿", "ult_dispel_and_abyss_flower");
        SECTION_TO_RULE.put("天赋：生息的轮转（开花结界）", "talent_zone_opens_at_two_stacks");
        SECTION_TO_RULE.put("天赋：生息的轮转（结界治疗攻击者）", "talent_zone_heals_the_attacker");
        SECTION_TO_RULE.put("天赋：生息的轮转（罗刹倒下解结界）", "talent_zone_ends_when_he_falls");
        SECTION_TO_RULE.put("秘技：愚者的悲悯", "technique_triggers_talent");
        SECTION_TO_RULE.put("星魂 1：濯洗生者", "eidolon1_attack_while_the_zone_is_up");
        SECTION_TO_RULE.put("星魂 2：净庭赐礼（低于半血治疗量）", "eidolon2_heal_boost_below_half");
        SECTION_TO_RULE.put("星魂 2：净庭赐礼（不低于半血护盾）", "eidolon2_shield_at_or_above_half");
        SECTION_TO_RULE.put("星魂 3：愚人试探", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：荆冠审判", "eidolon4_weaken_while_the_zone_is_up");
        SECTION_TO_RULE.put("星魂 5：受难之痕", "eidolon5_ultra_and_talent_levels");
        SECTION_TO_RULE.put("星魂 6：皆归尘土", "ult_all_type_res_down");
        SECTION_TO_RULE.put("行迹：浸池苏生", "trace_dispel_on_skill_trigger");
        SECTION_TO_RULE.put("行迹：浇灌尘身", "trace_zone_heals_the_others");
        SECTION_TO_RULE.put("行迹：行过幽谷", "trace_control_resist");
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
        System.out.println("[1203] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1203.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}