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
 * The two replay characters with a resource-level {@code note}: 1415 Cyrene (昔涟) and 1512 Robin (知更鸟•晴歌).
 *
 * <p>They are the case the build-time strip was fixed for: a rule's {@code note} becomes {@code _note}, while a
 * {@code ResourceSpec.note} stays {@code note} because it is a runtime field.
 */
public class CyreneAndRobinFormatMigrationTest {

    /**
     * Cyrene's enhanced basic is data slot 8; it is the source of the ENHANCED_BASIC skill type.
     */
    @Test
    public void cyrenesEnhancedBasicRowLivesInTheSource() throws Exception {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1415.json");
        JsonObject structured = null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                if (rule.has("source") && rule.get("source").isJsonObject()
                        && "sharpened_basic_gives_three_recollection".equals(rule.get("id").getAsString())) {
                    structured = rule.getAsJsonObject("source");
                }
            }
        }
        Assertions.assertNotNull(structured);
        Assertions.assertEquals("ENHANCED_BASIC", structured.get("skill").getAsString());
        Assertions.assertEquals(1, structured.get("row").getAsInt());
        Assertions.assertEquals(3.0, structured.getAsJsonObject("params").get("#2").getAsDouble());
    }

    /**
     * Robin's ultimate and its guest-attack clause quote the same ULTRA row; both are structured sources.
     */
    @Test
    public void robinsUltimateRowsLiveInSourceObjects() throws Exception {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1512.json");
        int structured = 0;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                JsonElement source = rule.get("source");
                if (source != null && source.isJsonObject()
                        && source.getAsJsonObject().has("skill")
                        && source.getAsJsonObject().has("row")
                        && source.getAsJsonObject().has("params")) {
                    structured++;
                }
            }
        }
        Assertions.assertEquals(2, structured, "the ultimate and guest-attack rows are structured");
    }
}
