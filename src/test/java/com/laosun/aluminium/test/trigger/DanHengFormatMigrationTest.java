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
 * The second content file on the new trigger skeleton: 1002 Dan Heng (丹恒).
 *
 * <p>1001 is the sample with `value`, `skills` and `ratio`; 1002 is the sample for the
 * <b>annotation-only</b> half: every rule's legacy {@code note} is now {@code _note}, and every rule carries
 * {@code _status: implemented}. The rule semantics are unchanged and are pinned by the existing {@code DanHeng*} judges.
 */
public class DanHengFormatMigrationTest {

    private static final int DAN_HENG = 1002;

    @Test
    public void everyRuleUsesUnderscoreNoteAndImplementedStatus() {
        JsonArray rules = rules();
        Assertions.assertTrue(rules.size() > 0);
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            String id = String.valueOf(rule.get("id"));
            Assertions.assertFalse(rule.has("note"), "legacy note remains on " + id);
            Assertions.assertTrue(rule.has("_note"), "rule has no _note: " + id);
            Assertions.assertEquals("implemented", rule.get("_status").getAsString(), id);
            Assertions.assertTrue(rule.has("source"), "source stays for error messages: " + id);
        }
    }

    /**
     * The Skill and Ultimate rows are the first two 1002 rules whose parameter row lives in the structured source.
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
        Assertions.assertEquals(2, structured, "1002's Skill and Ultimate rows are structured");
    }

    private static JsonArray rules() {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1002.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("rules");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), e);
        }
    }
}
