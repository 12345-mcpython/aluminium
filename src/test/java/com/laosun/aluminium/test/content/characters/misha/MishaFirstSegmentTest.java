package com.laosun.aluminium.test.content.characters.misha;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 1312 米沙's trace 释放: 「终结技<b>首段</b>攻击前，使目标陷入冻结状态的基础概率提高 80%」.
 *
 * <p><b>What it needed.</b> Nothing in the event said "which segment this is": {@code hit_count} counts TARGETS. The
 * segment ordinal now rides the settled instance ({@code Damage.hitIndex}) and is readable as {@code hit_index}, so the
 * sentence is written as TWO rules: the trace's guaranteed first segment (20% + 80% = 100% base chance) and the base
 * per-segment 20% gated to {@code hit_index > 0} - otherwise the first segment would roll twice.
 *
 * <p>The discriminator is the roll: with it forced to FAIL, the plain 20% clause never lands, so a frozen enemy can only
 * come from the first segment's 100%.
 */
public class MishaFirstSegmentTest {
    private static final int MISHA = 1312;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * With the roll forced to fail, only the trace's first segment can freeze - and it does.
     */
    @Test
    public void theFirstSegmentFreezesEvenWhenThePerSegmentRollFails() {
        Assertions.assertTrue(frozenAfterUltimate(0.99),
                "the first segment's base chance is 20% + 80% = 100%, so a failing roll still freezes it");
    }

    /**
     * The two rules are exclusive by construction: the shipped file gates the base clause to later segments.
     */
    @Test
    public void theShippedRulesSplitTheSegments() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1312.json");
        JsonObject file1 = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject base = rule(file1, "ult_freeze_per_segment");
        JsonObject trace = rule(file1, "trace_the_first_segment_freezes_almost_surely");

        Assertions.assertTrue(base.getAsJsonArray("when").toString().contains("hit_index > 0"),
                "the 20% clause must skip the first segment, or it would roll a second time there");
        Assertions.assertTrue(trace.getAsJsonArray("when").toString().contains("hit_index == 0"),
                "the trace names the first segment");
        Assertions.assertEquals(1.0,
                trace.getAsJsonArray("do").get(0).getAsJsonObject().get("base_chance").getAsDouble(), 1e-9,
                "「提高 80%」 on top of the 20% base is a certainty (still resistible)");
    }

    // ==================================================================

    private static JsonObject rule(JsonObject file, String id) {
        for (var element : file.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (id.equals(rule.get("id").getAsString())) {
                return rule;
            }
        }
        throw new AssertionError("rule not found: " + id);
    }

    private static boolean frozenAfterUltimate(double roll) {
        Character misha = CharacterFactory.create(MISHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        // The attribute dummy, not BattleFixture.monster(): that enemy resists 冻结 outright (see MishaFreezeTest).
        Enemy enemy = BattleFixture.dummy();
        Battle battle = new Battle(List.of(misha, ally), List.of(enemy), BattleFixture.alwaysRoll(roll));
        battle.startBattle();
        battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
        battle.processRequests();
        return enemy.getBuffManager().hasState("ctrl:frozen");
    }
}
