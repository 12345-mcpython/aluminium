package com.laosun.aluminium.test.trigger;

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
 * The first migrated LIGHT CONE: 23055 "献给明日的色彩" (the fully implemented cone).
 *
 * <p>Its file is keyed by superimposition rank ({@code "1"}.."5"}), and each rank has the same rule id. The migration
 * renames the rule's note and adds a status per rank, not once per file.
 */
public class Cone23055FormatMigrationTest {

    @Test
    public void everyRankUsesUnderscoreNoteAndImplementedStatus() {
        Path file = Path.of("src", "main", "resources", "game", "light_cones", "23055.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String rank : new String[]{"1", "2", "3", "4", "5"}) {
                for (JsonElement element : root.getAsJsonArray(rank)) {
                    JsonObject rule = element.getAsJsonObject();
                    String id = "rank " + rank + " / " + rule.get("id").getAsString();
                    Assertions.assertFalse(rule.has("note"), "legacy note remains on " + id);
                    Assertions.assertTrue(rule.has("_note"), "rule has no _note: " + id);
                    Assertions.assertEquals("implemented", rule.get("_status").getAsString(), id);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), e);
        }
    }
}
