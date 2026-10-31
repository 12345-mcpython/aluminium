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
import java.util.stream.Stream;

/**
 * Every shipped character rule uses the annotation spelling {@code _note}; every resource declaration keeps
 * {@code note}, because that one is a {@code ResourceSpec} field.
 *
 * <p>This is the mass migration's guard: a new rule written with the old key, or a resource that gets the annotation
 * spelling by accident, is a red test rather than a silent shape divergence.
 */
public class CharacterNoteSpellingTest {

    @Test
    public void rulesUseUnderscoreNoteAndResourcesKeepNote() throws Exception {
        Path root = Path.of("src", "main", "resources", "game", "characters");
        Assertions.assertTrue(Files.isDirectory(root), "character directory must exist: " + root.toAbsolutePath());

        List<String> problems = new ArrayList<>();
        int ruleNotes = 0;
        int resourceNotes = 0;
        int files = 0;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                files++;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement rootElement = JsonParser.parseReader(reader);
                    JsonArray rules = rootElement.isJsonArray() ? rootElement.getAsJsonArray()
                            : rootElement.getAsJsonObject().getAsJsonArray("rules");
                    for (JsonElement element : rules) {
                        JsonObject rule = element.getAsJsonObject();
                        if (rule.has("note")) {
                            problems.add(file.getFileName() + " rule " + rule.get("id") + " still has \"note\"");
                        }
                        if (rule.has("_note")) {
                            ruleNotes++;
                        }
                    }
                    if (rootElement.isJsonObject() && rootElement.getAsJsonObject().has("resources")) {
                        for (JsonElement element : rootElement.getAsJsonObject().getAsJsonArray("resources")) {
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
        Assertions.assertTrue(files > 50, "expected the shipped character content, found " + files + " files");
        Assertions.assertTrue(ruleNotes > 0, "no rule carries _note; the migration did not run");
        Assertions.assertEquals(List.of(), problems,
                "the note spelling diverged:\n  " + String.join("\n  ", problems));
    }
}
