package com.laosun.aluminium.test.content.characters.saber;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1014 Saber: 「消耗所有【炉心共鸣】为 Saber <b>固定</b>恢复能量」 / 天赋「每消耗 1 点为 Saber <b>固定</b>恢复 <b>8.00</b> 点能量」.
 *
 * <p><b>Why a spec-level case.</b> 「固定恢复」 is the engine's {@code fixed: true} on {@code GAIN_ENERGY}: a gain that skips the
 * energy-regeneration rate. The clause cannot be measured through the bar here - the 炉心共鸣 refund is only allowed when it
 * fills the bar, so the settled number is the cap either way and the difference hides in the overflow bank. What is checkable,
 * and what a mutation would break, is the flag itself: with it the refund is the stated amount, without it a character holding
 * +50% regeneration would refund 12 points per layer instead of 8.
 */
public class SaberResonanceRefundIsFixedTest {

    private static final Path SABER = Path.of("src", "main", "resources", "game", "characters", "1014.json");

    @Test
    public void bothResonanceRefundsAreFixedGains() throws IOException {
        JsonObject file = JsonParser.parseString(Files.readString(SABER, StandardCharsets.UTF_8)).getAsJsonObject();
        Assertions.assertTrue(refundIsFixed(file, "skill_resonance_boost_and_spend"),
                "「为 Saber 固定恢复能量」 — the refund must skip the energy-regeneration rate");
        Assertions.assertTrue(refundIsFixed(file, "skill_resonance_boost_with_eidolon2"),
                "and the Eidolon-2 branch of the same sentence says the same about the same number");
    }

    /**
     * @return whether that rule's {@code GAIN_ENERGY} (the 炉心共鸣 refund) carries {@code fixed: true}
     */
    private static boolean refundIsFixed(JsonObject file, String id) {
        for (var element : file.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!id.equals(rule.get("id").getAsString())) {
                continue;
            }
            for (var effect : rule.getAsJsonArray("do")) {
                JsonObject op = effect.getAsJsonObject();
                if (!"GAIN_ENERGY".equals(op.get("op").getAsString())) {
                    continue;
                }
                Assertions.assertTrue(op.has("value"), id + ": the refund is a per-layer product, not a literal");
                return op.has("fixed") && op.get("fixed").getAsBoolean();
            }
            throw new AssertionError(id + " has no GAIN_ENERGY effect any more");
        }
        throw new AssertionError("rule not found: " + id);
    }
}
