package com.laosun.aluminium.test.data;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * All shipped trigger content uses {@code _note} for a rule's note and keeps {@code note} only where it is a runtime
 * field ({@code ResourceSpec.note}).
 *
 * <p>The three shapes are covered: a character file is an array or an object with {@code rules}; a light cone and a
 * relic set are objects whose numeric keys ({@code "1"}..{@code "5"}, {@code "2"} / {@code "4"}) each hold an array of
 * rules. This is the guard for the 2026-10-31 mass migration.
 */
public class AllContentNoteSpellingTest {

    @Test
    public void rulesUseUnderscoreNoteAndResourcesKeepNote() throws Exception {
        List<String> problems = new ArrayList<>();
        int files = 0;
        int ruleNotes = 0;
        int resourceNotes = 0;

        for (String dir : List.of("characters", "light_cones", "relic_sets")) {
            Path root = Path.of("src", "main", "resources", "game", dir);
            Assertions.assertTrue(Files.isDirectory(root), "content directory must exist: " + root.toAbsolutePath());
            try (Stream<Path> paths = Files.list(root)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    files++;
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonElement rootElement = JsonParser.parseReader(reader);
                        if (rootElement.isJsonArray()) {
                            ruleNotes += checkRules(file, rootElement.getAsJsonArray(), problems);
                        } else {
                            JsonObject object = rootElement.getAsJsonObject();
                            if (object.has("rules")) {
                                ruleNotes += checkRules(file, object.getAsJsonArray("rules"), problems);
                            }
                            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                                if (entry.getValue().isJsonArray() && !"resources".equals(entry.getKey())) {
                                    ruleNotes += checkRules(file, entry.getValue().getAsJsonArray(), problems);
                                }
                            }
                            if (object.has("resources")) {
                                for (JsonElement element : object.getAsJsonArray("resources")) {
                                    JsonObject resource = element.getAsJsonObject();
                                    if (resource.has("_note")) {
                                        problems.add(file.getFileName() + " resource " + resource.get("id")
                                                + " has the annotation spelling");
                                    }
                                    if (resource.has("note")) {
                                        resourceNotes++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertTrue(files > 200, "expected all shipped content, found " + files + " files");
        Assertions.assertTrue(ruleNotes > 0, "no rule carries _note; the migration did not run");
        Assertions.assertEquals(List.of(), problems,
                "the note spelling diverged:\n  " + String.join("\n  ", problems));
        Assertions.assertTrue(resourceNotes > 0, "the resource-note exception is no longer represented");
    }

    private static int checkRules(Path file, JsonArray rules, List<String> problems) {
        int notes = 0;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rule = element.getAsJsonObject();
            if (!rule.has("on") && !rule.has("on_any") && !rule.has("op")) {
                continue;
            }
            String id = file.getFileName() + " rule "
                    + (rule.has("id") ? rule.get("id").getAsString()
                    : rule.has("on") ? rule.get("on").getAsString() : "(effect)");
            if (rule.has("note")) {
                problems.add(id + " still has \"note\"");
            }
            if (rule.has("_note")) {
                notes++;
            }
        }
        return notes;
    }
}
