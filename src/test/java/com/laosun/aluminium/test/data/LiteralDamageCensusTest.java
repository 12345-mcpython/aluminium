package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * The literal-ratio conversions from round 188's audit: the three rules that pointed at slot 1 (COMMON).
 *
 * <p>Why they were converted: `data/skills.json` puts 1.4 in the COMMON row's Lv10 parameter while every document says 100%, so a row reference deals a number
 * nobody stated. This census reads the shipped file, so an edit back to a row (or to another percent) fails here.
 */
public class LiteralDamageCensusTest {
    private static final double DOCUMENT = 1.0;

    /** Note: The three converted rules state the document's 100% literally, with an element and no row. */
    @Test
    public void theConvertedRulesStateTheDocumentNumberLiterally() {
        for (Object[] target : new Object[][]{{1109, "talent_followup_against_burning"},
                {1206, "skill_sword_stance_chance"}, {1206, "skill_sword_stance_guaranteed"}}) {
            String cid = String.valueOf(target[0]);
            String id = (String) target[1];
            JsonObject effect = damageEffectOf(cid, id);
            Assertions.assertFalse(effect.has("skill"),
                    "cid " + cid + " " + id + ": a row reference is what the audit removed — the COMMON row says 1.4, not 100%");
            Assertions.assertEquals("self_attr:ATTACK", effect.get("scale").getAsString(), "cid " + cid + " " + id + ": the scale");
            Assertions.assertEquals(DOCUMENT, effect.get("percent").getAsDouble(), 1e-9,
                    "cid " + cid + " " + id + ": the document says 100%");
            Assertions.assertTrue(effect.has("element"), "cid " + cid + " " + id + ": a literal ratio must state its element");
        }
    }

    /** Reads one rule's DAMAGE effect straight out of the shipped file. */
    private static JsonObject damageEffectOf(String cid, String ruleId) {
        String path = "/characters/" + cid + ".json";
        try (InputStream stream = LiteralDamageCensusTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            JsonArray rules = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement rule : rules) {
                JsonObject object = rule.getAsJsonObject();
                if (object.get("id") == null || !ruleId.equals(object.get("id").getAsString())) {
                    continue;
                }
                for (JsonElement effect : object.getAsJsonArray("do")) {
                    JsonObject candidate = effect.getAsJsonObject();
                    if (candidate.get("op") != null && "DAMAGE".equalsIgnoreCase(candidate.get("op").getAsString())) {
                        return candidate;
                    }
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
        throw new IllegalStateException("no DAMAGE rule " + ruleId + " for cid " + cid);
    }

    /** Note: The ship still loads: a battle with 1206 runs, so the converted rules pass the loader's validation. */
    @Test
    public void herFileStillLoads() {
        var hero = com.laosun.aluminium.utils.CharacterFactory.create(1206, 80);
        var enemy = com.laosun.aluminium.models.enemy.Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        });
        battle.startBattle();
        Assertions.assertTrue(hero.getCurrentHp() > 0);
    }
}
