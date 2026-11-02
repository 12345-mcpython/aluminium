package com.laosun.aluminium.test.content.characters.argenti;

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
 * 1302 银枝 clause coverage: every section of {@code 1302_银枝.md} maps to a rule or to the data row it reads.
 *
 * <p><b>Why this file exists now.</b> Its {@code _gaps} carried a single line that ended in 「⛔ 仍开：其余技能从句与星魂」 while every one of
 * those sections — the talent's stacks, the three traces, all six Eidolons, the technique and the enhanced Ultimate — already had a rule.
 * The status said {@code implemented}, so nothing ever re-read that line; this map is what settles it.
 */
public class ArgentiClauseCoverageTest {

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：芬芳一现", "talent_stack_basic");
        SECTION_TO_RULE.put("战技：公正，此处盛放", "talent_stack_skill");
        SECTION_TO_RULE.put("终结技：驻于花庭，赐与尽美", "talent_stack_ultra");
        SECTION_TO_RULE.put("天赋：崇高的客体", "talent_stack_ultra");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：纯粹高洁宣言", "technique_damage_and_energy");
        SECTION_TO_RULE.put("强化终结技：驻「我」华庭，授予至勋", "enhanced_ult_available");
        SECTION_TO_RULE.put("行迹：虔诚", "turn_start_effect");
        SECTION_TO_RULE.put("行迹：勇气", "on_damage_effect");
        SECTION_TO_RULE.put("行迹：慷慨", "trace_generous_energy");
        SECTION_TO_RULE.put("星魂 1：审美王国的缺口", "eidolon1_battle_start");
        SECTION_TO_RULE.put("星魂 2：玛瑙石的谦卑", "eidolon2_attack_with_three_enemies");
        SECTION_TO_RULE.put("星魂 3：荆棘路的荣光", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：号角的奉献", "eidolon4_battle_start");
        SECTION_TO_RULE.put("星魂 5：宇宙某处的雪", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：「你」的光芒", "eidolon6_on_damage");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            if (entry.getValue().startsWith("(")) {
                continue;
            }
            boolean found = false;
            for (var element : rules) {
                JsonObject candidate = element.getAsJsonObject();
                if (candidate.has("id") && entry.getValue().equals(candidate.get("id").getAsString())) {
                    found = true;
                    break;
                }
            }
            Assertions.assertTrue(found, entry.getKey() + " has no rule (expected " + entry.getValue() + ")");
        }
    }

    @Test
    public void theFileNoLongerClaimsGapsItDoesNotHave() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1302.json");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Assertions.assertEquals("implemented", root.get("_status").getAsString());
        Assertions.assertTrue(root.getAsJsonArray("_gaps").isEmpty(),
                "the old line said 「其余技能从句与星魂」 while all six Eidolons and the traces already had rules");
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1302.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }
}
