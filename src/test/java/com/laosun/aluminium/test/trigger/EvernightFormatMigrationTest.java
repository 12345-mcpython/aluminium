package com.laosun.aluminium.test.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The fifth content file on the new trigger skeleton: 1413 Evernight (长夜月), a replay character.
 *
 * <p>It is the first migrated file where some rules have no {@code id}; the check falls back to {@code on} for a
 * diagnostic name, and resources are left untouched.
 */
public class EvernightFormatMigrationTest {

    @Test
    public void everyRuleUsesUnderscoreNoteAndImplementedStatus() {
        JsonArray rules = rules();
        Assertions.assertTrue(rules.size() > 0);
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            String id = rule.has("id") ? rule.get("id").getAsString() : rule.get("on").getAsString();
            Assertions.assertFalse(rule.has("note"), "legacy note remains on " + id);
            Assertions.assertTrue(rule.has("_note"), "rule has no _note: " + id);
            Assertions.assertEquals("implemented", rule.get("_status").getAsString(), id);
        }
    }

    /**
     * Skill / Talent / Ultimate rows are the first three 1413 parameter rows in a structured source.
     */
    @Test
    public void parameterRowsLiveInSourceObjects() {
        int structured = 0;
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            JsonElement source = rule.get("source");
            if (source != null && source.isJsonObject()
                    && source.getAsJsonObject().has("skill")
                    && source.getAsJsonObject().has("row")
                    && source.getAsJsonObject().has("params")) {
                structured++;
            }
        }
        Assertions.assertEquals(3, structured, "the Skill / Talent / Ultimate rows are structured");
    }

    private static JsonArray rules() {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1413.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.getAsJsonObject().getAsJsonArray("rules");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), e);
        }
    }
}
