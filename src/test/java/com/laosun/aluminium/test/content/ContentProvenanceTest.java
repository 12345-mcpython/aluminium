package com.laosun.aluminium.test.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every shipped rule carries its <b>provenance</b>: the sentence it comes from ({@code source}) and what was decided
 * about it ({@code note}, or the annotation spelling {@code _note}).
 *
 * <p><b>Why a guard and not a convention.</b> The audit status of a character is only as good as its notes: a rule with
 * no {@code note} has nothing to re-check, and a rule with no {@code source} cannot be traced back to a document. 42 such
 * rules shipped before this guard existed (15 distinct file/id pairs - the light cones key their rules by rank, so one id
 * repeats across ranks). Every one of them has been filled, so the backlog is empty and there is no exception list.
 *
 * <p><b>Why there is no longer a {@code RECORDED} list.</b> It used to hold the backlog and this case failed in
 * <b>both</b> directions - a new gap, and a recorded gap that had been filled. The second direction turned progress
 * into a red: the only way to fix a gap without failing the gate was to edit the list in the same commit, and the
 * failure message ("delete their lines") made a correct change look like a regression. A to-do list that must be
 * edited to accept its own completion is a trap, not a guard. Should a backlog ever be needed again, record it in
 * the ledger ({@code CONTENT-STATUS.md}) where fixing an entry is the expected direction.
 */
public class ContentProvenanceTest {

    private static final Path ROOT = Path.of("src", "main", "resources", "game").toAbsolutePath();

    /** A rule that shipped without one half of its provenance. */
    private record Gap(String file, String id, String fields) {
    }

    @Test
    public void noRuleShipsWithoutItsSourceAndNote() throws IOException {
        Set<Gap> found = new LinkedHashSet<>();
        for (String dir : List.of("characters", "light_cones", "relic_sets")) {
            Path root = ROOT.resolve(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : files.filter(each -> each.toString().endsWith(".json")).toList()) {
                    if (file.getFileName().toString().startsWith("_")) {
                        continue;                       // registries, not content
                    }
                    collect(file, found);
                }
            }
        }

        List<String> gaps = new ArrayList<>();
        for (Gap gap : found) {
            gaps.add(gap.toString());
        }
        Assertions.assertTrue(gaps.isEmpty(), "rules shipped without source/note:\n  "
                + String.join("\n  ", gaps));
    }

    /**
     * Reads one content file and records the rules whose source or note is blank.
     */
    private static void collect(Path file, Set<Gap> found) throws IOException {
        JsonElement root;
        try (InputStream stream = Files.newInputStream(file);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        }
        List<JsonObject> rules = new ArrayList<>();
        if (root.isJsonArray()) {
            root.getAsJsonArray().forEach(each -> rules.add(each.getAsJsonObject()));
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("rules")) {
                object.getAsJsonArray("rules").forEach(each -> rules.add(each.getAsJsonObject()));
            }
            for (var entry : object.entrySet()) {
                if (entry.getKey().chars().allMatch(Character::isDigit) && entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(each -> rules.add(each.getAsJsonObject()));
                }
            }
        }
        for (JsonObject rule : rules) {
            List<String> missing = new ArrayList<>();
            if (blank(rule, "source")) {
                missing.add("source");
            }
            if (blank(rule, "note") && blank(rule, "_note")) {
                missing.add("note");
            }
            if (!missing.isEmpty()) {
                String id = rule.has("id") ? rule.get("id").getAsString() : "?";
                found.add(new Gap(file.getFileName().toString(), id, String.join("+", missing)));
            }
        }
    }

    private static boolean blank(JsonObject rule, String field) {
        JsonElement value = rule.get(field);
        if (value != null && value.isJsonObject() && "source".equals(field)) {
            value = value.getAsJsonObject().get("text");
        }
        return value == null || value.isJsonNull() || value.getAsString().isBlank();
    }
}
