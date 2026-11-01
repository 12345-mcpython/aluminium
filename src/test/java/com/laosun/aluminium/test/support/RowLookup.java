package com.laosun.aluminium.test.support;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laosun.aluminium.beans.EffectSpec;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Resolves a row-read share ( {@code percent_from_skill_param: "TALENT:1"} ) to the number the shipped table states.
 *
 * <p><b>Why tests need this.</b> A rule that reads a row no longer states {@code percent}, so a shape assertion that
 * unboxes {@code getPercent()} cannot see the number any more - and skipping it would let the shape check stop meaning
 * anything. This reads the same bundled {@code data/skills.json} the engine reads, at the <b>Lv10</b> row every file
 * pins, so an assertion can still say "the clause states 15%" and be telling the truth.
 *
 * <p>⚠ It pins Lv10 on purpose: a fixture that wears Eidolons which raise a slot reads a LATER row (measured: 1109's
 * Eidolon 3 moves the Talent to Lv12), and those cases belong in a behaviour test, not in a shape check.
 */
public final class RowLookup {

    private RowLookup() {
    }

    /** The share an effect states, whichever spelling it uses. */
    public static double shareOf(int cid, EffectSpec effect) {
        String spelling = effect.getPercentFromSkillParam();
        if (spelling == null) {
            return effect.getPercent();
        }
        return rowShare(cid, spelling);
    }

    /** One parameter of {@code <SLOT>:<index>} at Lv10, as {@code data/skills.json} states it. */
    public static double rowShare(int cid, String spelling) {
        String[] parts = spelling.split(":");
        int slot = switch (parts[0].trim().toUpperCase()) {
            case "COMMON" -> 1;
            case "SKILL" -> 2;
            case "ULTRA" -> 3;
            case "TALENT" -> 4;
            default -> throw new IllegalStateException("unknown slot in \"" + spelling + "\"");
        };
        int column = Integer.parseInt(parts[1].trim());
        try (InputStream stream = RowLookup.class.getResourceAsStream("/game/data/skills.json")) {
            if (stream == null) {
                throw new IllegalStateException("the bundled skills table is missing");
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            JsonElement rows = root.getAsJsonObject().getAsJsonObject(String.valueOf(cid))
                    .getAsJsonObject(String.valueOf(slot)).getAsJsonArray("param_list");
            return rows.getAsJsonArray().get(9).getAsJsonArray().get(column).getAsDouble();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + cid + " " + spelling, e);
        }
    }
}