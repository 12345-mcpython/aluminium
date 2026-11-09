package com.laosun.aluminium.test.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1310 流萤's 火萤Ⅳ型-死星过载:「对该目标造成等同于装甲「萨姆」（<b>#5×击破特攻 + #1%</b>）攻击力的火属性伤害…最多计算 <b>#7% 击破特攻</b>」.
 *
 * <p>Two things this pins, both invisible to a smoke test: the base is a SUM of a Breaking-Effect term and an Attack term, and the
 * Breaking-Effect term is <b>clamped</b> at the row's own #7 (360%) before it is multiplied. A cap that is silently ignored would
 * inflate every hit of a build with more than 360% BE - a plausible wrong number with no symptom.
 *
 * <p>⚠ The Skill is reached only through 【完全燃烧】's skill swap, which this file does not implement yet, so the judge reads the
 * curated table. That is deliberate: the reading is wrong whether or not the skill can be cast today.
 */
public class SamDeathstarBaseTest {
    private static final Path DATA = Path.of("src/main/resources/game/data");

    @Test
    public void theBreakingEffectTermCarriesItsOwnCap() throws IOException {
        JsonObject curated = JsonParser.parseString(
                Files.readString(DATA.resolve("skill_effects.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject row = curated.getAsJsonObject("1310").getAsJsonObject("9");
        Assertions.assertTrue(row.has("base_terms"), "火萤Ⅳ型-死星过载 states a two-term base");

        JsonObject breaking = null;
        JsonObject attack = null;
        for (JsonElement term : row.getAsJsonArray("base_terms")) {
            JsonObject one = term.getAsJsonObject();
            if ("BREAKING_EFFECT".equals(one.get("attribute").getAsString())) {
                breaking = one;
            } else if ("ATTACK".equals(one.get("attribute").getAsString())) {
                attack = one;
            }
        }
        Assertions.assertNotNull(breaking, "one term is scaled by 击破特攻");
        Assertions.assertNotNull(attack, "and one by 攻击力");
        Assertions.assertEquals(4, breaking.get("main").getAsInt(), "#5 prices the centre's BE term");
        Assertions.assertEquals(5, breaking.get("adjacent").getAsInt(), "#6 prices the neighbours' BE term");
        Assertions.assertEquals(6, breaking.get("cap").getAsInt(),
                "「最多计算 #7% 击破特攻」 - the cap is the row's own column, not a literal");
        Assertions.assertFalse(attack.has("cap"), "the Attack term is not capped");
        Assertions.assertEquals(0, attack.get("main").getAsInt(), "#1 prices the centre's ATK term");
        Assertions.assertEquals(1, attack.get("adjacent").getAsInt(), "#2 prices the neighbours' ATK term");
    }
}
