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
 * Every rule in an {@code _status: "implemented"} character file carries the two annotations a reviewer needs:
 * {@code _note} (why the rule is written the way it is) and {@code source} (the text a load failure quotes).
 *
 * <p>Before this test, five {@code *FormatMigrationTest} files each checked the same thing for one character. The
 * migration is done; the invariant is corpus-wide now, and a new implemented rule that omits either annotation is a red
 * test without a new per-character class.
 */
public class ImplementedRuleAnnotationsTest {

    private static final Path CHARACTERS = Path.of("src", "main", "resources", "game", "characters");

    @Test
    public void everyImplementedCharacterRuleCarriesNoteAndSource() throws Exception {
        Assertions.assertTrue(Files.isDirectory(CHARACTERS),
                "character directory must exist: " + CHARACTERS.toAbsolutePath());

        List<String> problems = new ArrayList<>();
        int files = 0;
        int rules = 0;
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject root;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    root = element.getAsJsonObject();
                }
                JsonElement status = root.get("_status");
                if (status == null || !"implemented".equals(status.getAsString())) {
                    continue;
                }
                files++;
                JsonArray array = root.getAsJsonArray("rules");
                if (array == null) {
                    problems.add(file.getFileName() + ": implemented file has no rules array");
                    continue;
                }
                for (JsonElement element : array) {
                    JsonObject rule = element.getAsJsonObject();
                    rules++;
                    String id = file.getFileName() + " / " + (rule.has("id") ? rule.get("id").getAsString() : "?");
                    if (rule.has("note")) {
                        problems.add(id + ": legacy \"note\" is still present");
                    }
                    if (!rule.has("_note")) {
                        problems.add(id + ": no \"_note\"");
                    }
                    if (!rule.has("source")) {
                        problems.add(id + ": no \"source\"");
                    }
                }
            }
        }

        Assertions.assertTrue(files > 30, "expected the implemented character corpus, found " + files + " files");
        Assertions.assertTrue(rules > 500, "expected the implemented rules, found " + rules);
        Assertions.assertEquals(List.of(), problems,
                "implemented character rules with a missing annotation:\n  " + String.join("\n  ", problems));
        System.out.println("[implemented-annotations] files=" + files + " rules=" + rules);
    }
}
