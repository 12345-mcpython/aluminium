package com.laosun.aluminium.test.content.characters.tribbie;

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
 * 1403 缇宝 clause coverage.
 *
 * <p>Her Ultimate states the Zone, its vulnerability and its additional damage in three sentences, and 星魂 4 / 6 each have two
 * halves; the map names one rule per sentence. The two 「献予「门径」之诗」 rules cite 1415's page (that is where the ode text
 * lives), which is why they are listed here rather than under a 缇宝 section.
 */
public class TribbieClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：一百层的小火箭", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：礼物都去哪儿了", "skill_numinosity");
        SECTION_TO_RULE.put("终结技：结界状态", "ult_zone_state");
        SECTION_TO_RULE.put("终结技：结界易伤", "ult_zone_enemy_vulnerability");
        SECTION_TO_RULE.put("终结技：结界的附加伤害", "ult_zone_additional_damage");
        SECTION_TO_RULE.put("天赋：追加攻击", "talent_followup_on_other_ult");
        SECTION_TO_RULE.put("天赋：终结技重置计数", "ult_resets_the_per_actor_followup_count");
        SECTION_TO_RULE.put("秘技：开心你就拍拍手", "technique_numinosity");
        SECTION_TO_RULE.put("星魂 1：额外真实伤害", "eidolon1_true_damage_on_zone_target");
        SECTION_TO_RULE.put("星魂 2：附加伤害提高", "eidolon2_zone_additional_damage");
        SECTION_TO_RULE.put("星魂 3：终结技与普攻等级", "eidolon3_ultra_and_common_levels");
        SECTION_TO_RULE.put("星魂 4：战技的无视防御", "eidolon4_party_defence_ignore_on_skill");
        SECTION_TO_RULE.put("星魂 4：秘技的无视防御", "eidolon4_party_defence_ignore_on_technique");
        SECTION_TO_RULE.put("星魂 5：战技与天赋等级", "eidolon5_skill_and_talent_levels");
        SECTION_TO_RULE.put("星魂 6：自己终结技后追加攻击", "eidolon6_own_ult_talent_followup");
        SECTION_TO_RULE.put("星魂 6：追加攻击增伤", "eidolon6_other_ult_talent_damage_boost");
        SECTION_TO_RULE.put("行迹：岔路旁的小石子？（开局 30 能量）", "trace_30_energy");
        SECTION_TO_RULE.put("行迹：岔路旁的小石子？（每目标回能）", "trace_energy_per_target");
        SECTION_TO_RULE.put("行迹：城墙外的羊羔儿…", "trace_damage_boost_after_followup");
        SECTION_TO_RULE.put("忆灵技：献予「门径」之诗（无视防御）", "memosprite_ode_of_passage_makes_his_damage_ignore_defence");
        SECTION_TO_RULE.put("忆灵技：献予「门径」之诗（额外附加伤害）", "passage_ode_adds_one_more_zone_hit");
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
        System.out.println("[1403] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1403.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}