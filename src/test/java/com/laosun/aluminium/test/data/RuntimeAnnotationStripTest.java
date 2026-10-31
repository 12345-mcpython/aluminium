package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The runtime classpath copy of a trigger file has its annotations stripped at build time.
 *
 * <p>The source file keeps them (that is what a reviewer edits); the engine never reads them. `source` is the one
 * provenance string kept for load-error messages. This test loads the classpath copy, so it goes red if the build task
 * stops running (and, because the source file still has `_note`, also if the task starts writing the sources instead of
 * the runtime copy).
 */
public class RuntimeAnnotationStripTest {

    @Test
    public void theClasspathCopyCarriesNoAnnotationsButKeepsSource() {
        try (Reader reader = new InputStreamReader(
                RuntimeAnnotationStripTest.class.getResourceAsStream("/game/characters/1001.json"),
                StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray rules = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().getAsJsonArray("rules");
            Assertions.assertTrue(rules.size() > 0, "the runtime copy still has its rules");
            JsonObject first = rules.get(0).getAsJsonObject();
            Assertions.assertFalse(first.has("_note"), "the annotation is stripped from the runtime copy");
            Assertions.assertFalse(first.has("note"), "and so is the legacy spelling");
            Assertions.assertTrue(first.has("source"), "source stays: load failures quote it");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read the classpath copy of 1001", e);
        }
    }

    @Test
    public void resourceNotesAreRuntimeFieldsAndSurviveTheStrip() {
        try (Reader reader = new InputStreamReader(
                RuntimeAnnotationStripTest.class.getResourceAsStream("/game/characters/1015.json"),
                StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonElement resources = root.getAsJsonObject().get("resources");
            Assertions.assertNotNull(resources, "1015 declares resources");
            boolean found = false;
            for (JsonElement resource : resources.getAsJsonArray()) {
                if (resource.getAsJsonObject().has("note")) {
                    found = true;
                }
            }
            Assertions.assertTrue(found, "ResourceSpec.note is a runtime field and is not stripped");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read the classpath copy of 1015", e);
        }
    }
}
