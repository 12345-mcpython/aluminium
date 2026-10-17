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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A rule that reads a skill parameter <b>at the skill's current level</b> must not run before the rule that sets that
 * level.
 *
 * <p><b>Why this is a guard and not a comment.</b> The engine's characters have no progression model: every slot starts
 * at level 1, and a file states 「the document quotes Lv10」 as a {@code level_convention} rule of its own
 * ({@code RAISE_SKILL_LEVEL +9}). Rules of the <b>same event</b> run in <b>file order</b>, so a rule that reads a row
 * depends on where its file put the convention. Measured (2026-10-17): 1304 Aventurine (砂金)'s 「热手」 trace shield is
 * a {@code BATTLE_START} rule placed <b>before</b> its convention, and once the shield's numbers were read out of the
 * Skill's row instead of written as literals it silently produced 0.16 x DEF + 80 (row 1) where the document says
 * 0.24 x DEF + 320 (row 10) - a wrong number with no symptom, and only a content test's exact expectation caught it.
 *
 * <p><b>What is and is not a hazard.</b> Only events that run <b>before or at</b> the level convention matter. A cast
 * event ({@code SKILL_CAST} / {@code ULT_CAST} / {@code BASIC_ATTACK}) always happens after {@code BATTLE_START}, so
 * its position in the file is irrelevant and is not flagged - 1001 March 7th (三月七)'s shield sits at the top of its
 * file and reads row 10 correctly.
 */
public class LevelConventionOrderTest {

    /**
     * Events at or before the point where a file's {@code level_convention} rule fires.
     */
    private static final Set<String> EARLY_EVENTS = Set.of("BATTLE_START", "BATTLE_READY", "WAVE_START");

    /**
     * The spellings that read a skill row at its current level (a {@code damage_param} read is not one of them: it goes
     * through {@code multiplierOf}, which honours {@code damage_level} and does not depend on the file's order).
     */
    private static final Set<String> LEVEL_READING_KEYS =
            Set.of("percent_from_skill_param", "amount_from_skill_param", "percent_from_cast_param");

    @Test
    public void anEarlyRuleNeverReadsASkillRowBeforeTheConventionSetsIt() {
        List<String> offenders = new ArrayList<>();
        int files = 0;
        for (Path p : characterFiles()) {
            files++;
            JsonArray rules = readRules(p);
            int conventionAt = -1;
            for (int i = 0; i < rules.size(); i++) {
                JsonObject r = rules.get(i).getAsJsonObject();
                if ("level_convention".equals(string(r, "id"))) {
                    conventionAt = i;
                    break;
                }
            }
            for (int i = 0; i < rules.size(); i++) {
                JsonObject r = rules.get(i).getAsJsonObject();
                if (!runsEarly(r)) {
                    continue;
                }
                if (!readsASkillRow(r)) {
                    continue;
                }
                if (conventionAt < 0 || conventionAt > i) {
                    offenders.add(p.getFileName() + " rule #" + i + " (id=" + string(r, "id") + ", on="
                            + string(r, "on") + "): convention at " + conventionAt);
                }
            }
        }

        Assertions.assertTrue(files > 50, "expected the shipped character content, found " + files + " files");
        Assertions.assertEquals(List.of(), offenders,
                "these rules read a skill row at its current level on an event that runs BEFORE the file's "
                        + "`level_convention` rule, so they read row 1 while the document quotes another row; "
                        + "move the convention to the top of the file (it is a fact about the file, not a mechanic)");
    }

    /**
     * The character files, as the shipped content directory holds them.
     */
    private static List<Path> characterFiles() {
        Path root = Path.of("src", "main", "resources", "characters");
        Assertions.assertTrue(Files.isDirectory(root), "the character directory must exist: " + root.toAbsolutePath());
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot list " + root, e);
        }
    }

    /**
     * A file is either an array of rules or an object with a {@code rules} array.
     */
    private static JsonArray readRules(Path p) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root.isJsonArray()) {
                return root.getAsJsonArray();
            }
            return root.getAsJsonObject().getAsJsonArray("rules");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + p, e);
        }
    }

    private static boolean runsEarly(JsonObject rule) {
        if (EARLY_EVENTS.contains(string(rule, "on"))) {
            return true;
        }
        JsonElement any = rule.get("on_any");
        if (any != null && any.isJsonArray()) {
            for (JsonElement e : any.getAsJsonArray()) {
                if (EARLY_EVENTS.contains(e.getAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean readsASkillRow(JsonObject rule) {
        JsonElement dos = rule.get("do");
        if (dos == null || !dos.isJsonArray()) {
            return false;
        }
        for (JsonElement e : dos.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                continue;
            }
            for (String key : LEVEL_READING_KEYS) {
                if (e.getAsJsonObject().has(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
