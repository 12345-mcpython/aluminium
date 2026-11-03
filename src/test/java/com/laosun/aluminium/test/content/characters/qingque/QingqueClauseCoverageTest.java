package com.laosun.aluminium.test.content.characters.qingque;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1201 青雀: Eidolon 6 — 「施放强化普攻后，恢复 1 个战技点」.
 *
 * <p><b>Why a shape test and not a behaviour test.</b> The enhanced Basic ATK is data slot 8, and the only thing that puts it in
 * her Basic slot is 【暗杠】 — which is still registered as needing three per-suit counters. Until that exists the fixture cannot
 * build an enhanced Basic ATK, so what is pinned here is the rule's shape: the event that follows the cast, the slot that means
 * "enhanced", and the Eidolon gate. The behaviour test lands with 【暗杠】.
 */
public class QingqueClauseCoverageTest {

    /**
     * The rule reads the enhanced Basic ATK by slot and pays one point after it.
     */
    @Test
    public void eidolon6PaysOnePointAfterTheEnhancedBasic() throws IOException {
        JsonObject rule = rule("eidolon6_the_enhanced_basic_refunds_a_point");
        Assertions.assertEquals("BASIC_ATTACK", rule.get("on").getAsString(), "「施放…后」 — the event after the cast");
        Assertions.assertEquals(6, rule.get("min_eidolon").getAsInt(), "it is Eidolon 6");
        Assertions.assertTrue(rule.getAsJsonArray("when").toString().contains("from_skill_id == 8"),
                "「强化普攻」 is data slot 8 (120108)");
        Assertions.assertTrue(rule.getAsJsonArray("do").toString().contains("GAIN_SKILL_POINT"),
                "「恢复 1 个战技点」");
    }

    /**
     * The document's own sentence explains why this Eidolon exists at all: the enhanced Basic ATK normally refunds nothing.
     */
    @Test
    public void theEnhancedBasicIsTheOneThatNormallyRefundsNothing() throws IOException {
        Path doc = Path.of("E:\\turnbasedgamedata\\aluminium_texts\\1201_青雀.md");
        Assertions.assertTrue(Files.exists(doc), "the external corpus is where the sentence lives");
        String text = Files.readString(doc, StandardCharsets.UTF_8);
        Assertions.assertTrue(text.contains("【杠上开花！】无法恢复战技点"),
                "precondition: the enhanced basic states the very rule Eidolon 6 exempts");
    }

    /**
     * 星魂 4: 「施放战技后，有 24% 的固定概率获得【不求人】…持续至本回合结束」, and the state adds one follow-up per Basic ATK.
     */
    @Test
    public void eidolon4CanGrantTheStateAndTheStateAddsAFollowUp() throws IOException {
        Assertions.assertTrue(hasRuleAndState(), "the two rules are in the file");
    }

    private static boolean hasRuleAndState() throws IOException {
        JsonObject grant = rule("eidolon4_skill_may_grant_the_unasked_for_state");
        JsonObject use = rule("eidolon4_the_state_adds_a_follow_up");
        return grant.get("chance").getAsDouble() == 0.24
                && grant.getAsJsonArray("do").toString().contains("不求人")
                && use.get("on").getAsString().equals("ATTACK_FINISHED")
                && use.getAsJsonArray("when").toString().contains("不求人")
                && use.getAsJsonArray("do").toString().contains("COMMON");
    }

    private static JsonObject rule(String id) throws IOException {
        JsonArray rules = JsonParser.parseString(
                        Files.readString(Path.of("src", "main", "resources", "game", "characters", "1201.json"),
                                StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return candidate;
            }
        }
        throw new AssertionError("no rule " + id);
    }
}



