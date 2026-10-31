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
 * repeats across ranks). They are <b>recorded below</b>, so this case fails on any NEW gap and also fails once a recorded
 * one is filled without the list being updated: the list is a to-do, not a permission.
 */
public class ContentProvenanceTest {

    private static final Path ROOT = Path.of("src", "main", "resources", "game").toAbsolutePath();

    /** A rule that shipped without one half of its provenance. */
    private record Gap(String file, String id, String fields) {
    }

    /** The recorded backlog (2026-10-30). Fix one, delete its line. */
    private static final List<Gap> RECORDED = List.of(
            new Gap("1006.json", "skill_inserts_party_first_weakness", "source"),
            new Gap("1203.json", "talent_zone_heals_the_attacker", "source"),
            new Gap("1306.json", "talent_raises_skill_point_cap", "note"),
            new Gap("1309.json", "ult_zone_defence_ignore", "source"),
            new Gap("1310.json", "talent_energy_floor_at_battle_start", "note"),
            new Gap("1405.json", "talent_random_weakness_on_hit", "source"),
            new Gap("1406.json", "aura_damage_taken_while_present", "source"),
            new Gap("1410.json", "ult_zone_opens_and_lowers", "source"),
            new Gap("1504.json", "ults_immediate_talent", "source"),
            new Gap("23034.json", "cone23034_skill_point_count_skill", "note"),
            new Gap("23034.json", "cone23034_skill_point_count_ult", "note"),
            new Gap("23034.json", "cone23034_skill_point_pay_skill", "note"),
            new Gap("23034.json", "cone23034_skill_point_pay_ult", "note"),
            new Gap("23050.json", "cone23050_resets_the_weakness_count", "source"),
            new Gap("23050.json", "cone23050_weakness_grants_skill_point", "source")
    );

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

        List<String> newGaps = new ArrayList<>();
        for (Gap gap : found) {
            if (!RECORDED.contains(gap)) {
                newGaps.add(gap.toString());
            }
        }
        List<String> fixed = new ArrayList<>();
        for (Gap recorded : RECORDED) {
            if (!found.contains(recorded)) {
                fixed.add(recorded.toString());
            }
        }
        Assertions.assertTrue(newGaps.isEmpty(), "rules shipped without source/note:\n  "
                + String.join("\n  ", newGaps));
        Assertions.assertTrue(fixed.isEmpty(), "these recorded gaps are gone - delete their lines from RECORDED:\n  "
                + String.join("\n  ", fixed));
        Assertions.assertEquals(RECORDED.size(), found.size(), "backlog size");
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
