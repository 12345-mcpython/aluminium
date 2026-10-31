package com.laosun.aluminium.test.data;

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
 * An object-form character file states its overall status at the top level.
 *
 * <p>A bare array predates the object form and has nowhere to put a file-level status; every object-form file with
 * {@code rules} is required to carry one. The loader validates the value and the gap consistency; this test is the
 * corpus-level guard that a new object file does not silently omit it.
 */
public class FileStatusCoverageTest {

    @Test
    public void everyCharacterFileStatesItsFileStatus() throws Exception {
        Path root = Path.of("src", "main", "resources", "game", "characters");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> paths = Files.list(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement rootElement = JsonParser.parseReader(reader);
                    checked++;
                    if (!rootElement.isJsonObject()) {
                        problems.add(file.getFileName() + " is a bare array");
                        continue;
                    }
                    JsonObject object = rootElement.getAsJsonObject();
                    if (!object.has("rules")) {
                        problems.add(file.getFileName() + " has no rules array");
                    }
                    if (!object.has("_status")) {
                        problems.add(file.getFileName() + " has no file-level _status");
                    }
                }
            }
        }
        Assertions.assertTrue(checked > 50, "expected the character corpus, found " + checked);
        Assertions.assertEquals(List.of(), problems,
                "character files without the object-form status header:\n  " + String.join("\n  ", problems));
    }
}
