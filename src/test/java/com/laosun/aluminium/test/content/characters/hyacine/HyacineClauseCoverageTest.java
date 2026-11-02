package com.laosun.aluminium.test.content.characters.hyacine;

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
 * 1409 风堇 clause coverage - the widest file in the corpus (40 rules).
 *
 * <p>Her sections split heavily: the Skill and the Ultimate each have three sentences (summon / heal the party / heal the
 * memosprite), 星魂 1, 4 and 6 have two halves each (her share and 小伊卡's), and the four memosprite skills plus 「献予「天空」之诗」
 * are sections of their own. The map names one rule per clause so that dropping any single one is visible.
 */
public class HyacineClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：天气正好，万物可爱！", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：召唤 小伊卡", "skill_summons_ica");
        SECTION_TO_RULE.put("战技：治疗我方全体", "the_skill_heals_every_ally");
        SECTION_TO_RULE.put("战技：同时治疗小伊卡", "the_skill_also_heals_ikachi_itself");
        SECTION_TO_RULE.put("终结技：召唤 小伊卡", "ult_summons_ica");
        SECTION_TO_RULE.put("终结技：治疗我方与小伊卡", "ult_heals_party_and_ikachi");
        SECTION_TO_RULE.put("终结技：雨过天晴与生命上限", "ult_state_and_max_hp");
        SECTION_TO_RULE.put("天赋：治疗提高小伊卡伤害", "talent_healing_raises_ikachis_damage");
        SECTION_TO_RULE.put("秘技：治疗与生命上限", "technique_heals_and_raises_max_hp");
        SECTION_TO_RULE.put("星魂 1：雨过天晴提高生命上限", "eidolon_one_raises_max_hp_while_rain_clear");
        SECTION_TO_RULE.put("星魂 1：攻击后回复", "eidolon_one_heals_after_an_attack_while_rain_clear");
        SECTION_TO_RULE.put("星魂 2：掉血后加速", "eidolon_two_speed_on_hp_loss");
        SECTION_TO_RULE.put("星魂 3：终结技等级", "eidolon_three_raises_the_ult");
        SECTION_TO_RULE.put("星魂 4：超速加暴伤（她自己）", "eidolon_four_adds_crit_damage_per_point_of_speed");
        SECTION_TO_RULE.put("星魂 4：超速加暴伤（小伊卡）", "eidolon_four_adds_ikachis_crit_damage_too");
        SECTION_TO_RULE.put("星魂 5：战技与天赋等级", "eidolon_five_raises_skill_and_talent");
        SECTION_TO_RULE.put("星魂 6：全属性抗性穿透", "eidolon_six_gives_the_party_all_type_resistance_penetration");
        SECTION_TO_RULE.put("星魂 6：后入场者也覆盖", "eidolon_six_aura_covers_later_arrivals");
        SECTION_TO_RULE.put("星魂 6：小伊卡离场则结束", "eidolon_six_aura_ends_when_ikachi_leaves");
        SECTION_TO_RULE.put("星魂 6：清空改为 12%", "eidolon_six_clears_twelve_percent_instead");
        SECTION_TO_RULE.put("行迹：暴风停歇（生命上限）", "trace_windstorm_raises_her_max_hp_above_200_speed");
        SECTION_TO_RULE.put("行迹：暴风停歇（治疗量）", "trace_windstorm_raises_her_healing_per_point_of_speed");
        SECTION_TO_RULE.put("行迹：暴风停歇（小伊卡同享）", "trace_windstorm_raises_ikachis_healing_too");
        SECTION_TO_RULE.put("忆灵技 1：累计她自己治疗", "tally_counts_hyacines_own_healing");
        SECTION_TO_RULE.put("忆灵技 1：累计小伊卡治疗", "tally_counts_ikachis_healing_too");
        SECTION_TO_RULE.put("忆灵技 1：按累计造成伤害", "memosprite_rainclouds_deals_a_share_of_the_tally");
        SECTION_TO_RULE.put("忆灵技 1：清空一半", "memosprite_rainclouds_clears_half_the_tally");
        SECTION_TO_RULE.put("忆灵技 2：给小伊卡回血", "ikachi_pays_and_heals_whoever_lost_hp");
        SECTION_TO_RULE.put("忆灵技 2：雨过天晴后立即行动", "ikachi_acts_right_after_she_uses_a_skill_in_the_rain");
        SECTION_TO_RULE.put("忆灵技 2：行动后自身效果减 1", "ikachi_shortens_its_own_effects_after_it_acts");
        SECTION_TO_RULE.put("忆灵技 2：免疫负面", "ikachi_is_immune_to_negative_effects");
        SECTION_TO_RULE.put("忆灵技 3：被召唤回能", "memosprite_soaring_gives_her_energy_when_it_arrives");
        SECTION_TO_RULE.put("忆灵技 3：首次召唤额外回能", "memosprite_soaring_gives_thirty_more_on_its_first_arrival");
        SECTION_TO_RULE.put("忆灵技 4：消失时让她行动提前", "memosprite_fall_advances_her_action");
        SECTION_TO_RULE.put("忆灵技：献予「天空」之诗（回能）", "memosprite_ode_of_sky_gives_her_energy");
        SECTION_TO_RULE.put("忆灵技：献予「天空」之诗（战技消耗）", "casting_a_skill_spends_one_layer_of_the_sky_ode");
        SECTION_TO_RULE.put("忆灵技：献予「天空」之诗（终结技消耗）", "casting_the_ultimate_spends_one_layer_of_the_sky_ode");
        SECTION_TO_RULE.put("忆灵技：献予「天空」之诗（治疗份额）", "sky_ode_captures_its_healing_share");
        SECTION_TO_RULE.put("忆灵技：献予「天空」之诗（计入累计）", "sky_ode_adds_a_share_of_each_heal_to_ikachis_total");
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
        System.out.println("[1409] sections covered = " + checked + " rules + the basic attack's row");
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1409.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}