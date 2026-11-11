package com.laosun.aluminium.test.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one place where "every section of the character document has a rule, or a written reason why not" is checked.
 *
 * <p>Before this class the same check was copied into 26 {@code *ClauseCoverageTest} files, each with its own
 * {@code SECTION_TO_RULE} map and its own JSON reader. The map is data: it now lives in
 * {@code src/test/resources/clause-coverage.json}, and this test walks every entry. A failure still names the
 * character, the section and the missing rule id.
 *
 * <p>The check is deliberately the same one the per-character files made: a section either names a rule id that
 * exists in that character's {@code rules} array, or states why no rule is needed. It does not invent "coverage" from
 * a keyword scan; that is {@code ImplementedCharacterCoversEveryDocumentSectionTest}'s job.
 */
public class ClauseCoverageTest {

    private static final Path MANIFEST = Path.of("src", "test", "resources", "clause-coverage.json");
    private static final Path CHARACTERS = Path.of("src", "main", "resources", "game", "characters");

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonObject manifest = JsonParser.parseString(Files.readString(MANIFEST, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Assertions.assertFalse(manifest.isEmpty(), "the clause manifest is empty: " + MANIFEST);

        List<String> problems = new ArrayList<>();
        int ruleBacked = 0;
        int writtenReason = 0;
        for (var characterEntry : manifest.entrySet()) {
            String cid = characterEntry.getKey();
            JsonArray sections = characterEntry.getValue().getAsJsonObject().getAsJsonArray("sections");
            Assertions.assertNotNull(sections, cid + " has no sections array in " + MANIFEST);
            Set<String> ruleIds = ruleIds(cid);
            for (JsonElement element : sections) {
                JsonObject section = element.getAsJsonObject();
                String title = section.get("section").getAsString();
                boolean hasRule = section.has("rule");
                boolean hasReason = section.has("why");
                if (hasRule == hasReason) {
                    problems.add(cid + " 「" + title + "」: state exactly one of rule / why");
                    continue;
                }
                if (hasRule) {
                    ruleBacked++;
                    String rule = section.get("rule").getAsString();
                    if (!ruleIds.contains(rule)) {
                        problems.add(cid + " 「" + title + "」: no rule id '" + rule + "' in " + cid + ".json");
                    }
                } else {
                    writtenReason++;
                    if (section.get("why").getAsString().isBlank()) {
                        problems.add(cid + " 「" + title + "」: the written reason is blank");
                    }
                }
            }
            System.out.println("[" + cid + "] sections=" + sections.size() + " rules=" + ruleIds.size());
        }

        Assertions.assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        Assertions.assertTrue(ruleBacked > 0, "the manifest names no rule at all");
        System.out.println("[clause-coverage] rule-backed=" + ruleBacked + " written-reason=" + writtenReason
                + " characters=" + manifest.size());
    }

    private static Set<String> ruleIds(String cid) throws IOException {
        Path file = CHARACTERS.resolve(cid + ".json");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("id")) {
                ids.add(rule.get("id").getAsString());
            }
        }
        return ids;
    }
}
