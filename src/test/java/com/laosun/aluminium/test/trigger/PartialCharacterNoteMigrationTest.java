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
 * The first migrated files that are <b>not</b> fully implemented: 1004 Welt (瓦尔特), 1005 Kafka (卡芙卡), 1006.
 *
 * <p>Only the note spelling is migrated here; no {@code _status} is added, because these files still have registered
 * gaps (see CONTENT-STATUS) and a status is a claim that needs the gap list to be true.
 */
public class PartialCharacterNoteMigrationTest {

    @Test
    public void notesAreAnnotationsEvenWhenTheRuleIsNotComplete() throws Exception {
        for (int cid : new int[]{1004, 1005, 1006}) {
            Path file = Path.of("src", "main", "resources", "game", "characters", cid + ".json");
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonArray rules = root.isJsonArray() ? root.getAsJsonArray()
                        : root.getAsJsonObject().getAsJsonArray("rules");
                if (root.isJsonObject()) {
                    JsonObject fileRoot = root.getAsJsonObject();
                    Assertions.assertEquals("partial", fileRoot.get("_status").getAsString(), cid + " is not complete");
                    Assertions.assertTrue(fileRoot.has("_gaps") && fileRoot.getAsJsonArray("_gaps").size() > 0,
                            cid + " states at least one gap");
                }
                for (JsonElement element : rules) {
                    JsonObject rule = element.getAsJsonObject();
                    String id = cid + " / " + rule.get("id").getAsString();
                    Assertions.assertFalse(rule.has("note"), "legacy note remains on " + id);
                    Assertions.assertTrue(rule.has("_note"), "rule has no _note: " + id);
                }
            }
        }
    }
}
