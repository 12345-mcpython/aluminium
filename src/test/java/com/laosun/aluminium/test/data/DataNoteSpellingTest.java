package com.laosun.aluminium.test.data;

import com.google.gson.JsonElement;
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
 * The non-trigger data files use {@code _note} for their explanatory annotations too.
 *
 * <p>These files have no runtime {@code note} field (unlike {@code ResourceSpec} / {@code MemospriteSpec}), so the
 * underscore spelling is the only one that should appear. The trigger loader is not involved here; this guard keeps
 * the source corpus consistent.
 */
public class DataNoteSpellingTest {

    @Test
    public void dataFilesUseUnderscoreNote() throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "resources", "game", "data"))) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(files::add);
        }
        Path battleResources = Path.of("src", "main", "resources", "game", "battle_resources.json");
        if (Files.exists(battleResources)) {
            files.add(battleResources);
        }
        Assertions.assertFalse(files.isEmpty(), "the data corpus must exist");

        int notes = 0;
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (hasLegacyNote(root)) {
                    problems.add(file.getFileName() + " still has \"note\"");
                }
                notes += countNotes(root);
            }
        }
        Assertions.assertTrue(notes > 0, "no data annotation was found; the migration did not run");
        Assertions.assertEquals(List.of(), problems,
                "the data note spelling diverged:\n  " + String.join("\n  ", problems));
    }

    private static boolean hasLegacyNote(JsonElement element) {
        if (element.isJsonObject()) {
            if (element.getAsJsonObject().has("note")) {
                return true;
            }
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (hasLegacyNote(entry.getValue())) {
                    return true;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (hasLegacyNote(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countNotes(JsonElement element) {
        int count = 0;
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if ("_note".equals(entry.getKey())) {
                    count++;
                }
                count += countNotes(entry.getValue());
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                count += countNotes(child);
            }
        }
        return count;
    }
}
