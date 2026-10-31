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
 * The third content file on the new trigger skeleton: 1003 Himeko (姬子).
 *
 * <p>It is the first migrated file with a top-level {@code resources} declaration, so the check that only the
 * <b>rules</b> were renamed (not the declarations) is part of the point.
 */
public class HimekoFormatMigrationTest {

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
     * The technique row is the first 1003 parameter row written as a structured source.
     */
    @Test
    public void theTechniqueRowLivesInTheStructuredSource() {
        JsonObject technique = null;
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("id") && "technique_incomplete_combustion".equals(rule.get("id").getAsString())) {
                technique = rule;
            }
        }
        Assertions.assertNotNull(technique);
        JsonObject source = technique.getAsJsonObject("source");
        Assertions.assertEquals("TECHNIQUE", source.get("skill").getAsString());
        Assertions.assertEquals(1, source.get("row").getAsInt());
        Assertions.assertEquals(0.1, source.getAsJsonObject("params").get("#2").getAsDouble());
    }

    /**
     * The ultimate's extra-energy sentence quotes row 1; its own row-10 #1 differs, and the rule only uses #2.
     */
    @Test
    public void theUltimateRowIsTheOneTheTextQuotes() {
        JsonObject ultimate = null;
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("source") && rule.get("source").isJsonObject()
                    && rule.getAsJsonObject("source").get("text").getAsString().startsWith("1003 姬子 终结技 留待残烬")) {
                ultimate = rule;
            }
        }
        Assertions.assertNotNull(ultimate);
        JsonObject source = ultimate.getAsJsonObject("source");
        Assertions.assertEquals("ULTRA", source.get("skill").getAsString());
        Assertions.assertEquals(1, source.get("row").getAsInt());
        Assertions.assertEquals(1.38, source.getAsJsonObject("params").get("#1").getAsDouble());
        Assertions.assertEquals(5.0, source.getAsJsonObject("params").get("#2").getAsDouble());
    }

    private static JsonArray rules() {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1003.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() ? root.getAsJsonObject().getAsJsonArray("rules") : root.getAsJsonArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), e);
        }
    }
}
