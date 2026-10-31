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
 * The literal-ratio conversions from round 188's audit, kept as a census of what that audit decided - and narrowed on
 * 2026-10-19, when one of the two files was measured again.
 *
 * <p>Why they were converted: `data/skills.json` puts 1.4 in the <b>COMMON</b> row's Lv10 parameter while every document says
 * 100%, so a row reference <b>to that slot</b> deals a number nobody stated.
 *
 * <p>⚠ <b>The narrowing, and what the old expectation was pinning.</b> Round 188's conversion covered three rules: 1109's
 * {@code talent_followup_against_burning} and 1206's two 剑势 rules. The two 1206 rules stayed literals until the 2026-10-19
 * audit pointed out that the number they quote is not a COMMON number at all: it is item <b>#2 of her SKILL row</b>
 * ({@code [2.1, 1, 0.33]} at Lv10 - the sentence's 「100% 攻击力」 is that column, and it scales, 1.1 at Lv12 after Eidolon 5).
 * So they read {@code percent_from_skill_param: "SKILL:1"} now, which is the fix this census was protecting the *wrong half*
 * of: the bug was "pointed at COMMON", not "reads a row". Their number is pinned at BOTH ends of the level by
 * {@code SushangLevelRowsTest} instead - a literal census cannot see a level-dependent share at all.
 */
public class LiteralDamageCensusTest {
    private static final double DOCUMENT = 1.0;

    /**
     * Note: The converted rule still states the document's 100% literally, with an element and no row.
     */
    @Test
    public void theConvertedRulesStateTheDocumentNumberLiterally() {
        for (Object[] target : new Object[][]{{1109, "talent_followup_against_burning"}}) {
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

    /**
     * Reads one rule's DAMAGE effect straight out of the shipped file.
     */
    private static JsonObject damageEffectOf(String cid, String ruleId) {
        String path = "/game/characters/" + cid + ".json";
        try (InputStream stream = LiteralDamageCensusTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            JsonArray rules = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().getAsJsonArray("rules");
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

    /**
     * Note: The ship still loads: a battle with 1206 runs, so the converted rules pass the loader's validation.
     */
    @Test
    public void herFileStillLoads() {
        var hero = com.laosun.aluminium.utils.CharacterFactory.create(1206, 80);
        var enemy = com.laosun.aluminium.enemy.Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
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
