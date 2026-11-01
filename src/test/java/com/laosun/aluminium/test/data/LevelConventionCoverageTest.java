package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every character file states which level its own numbers were quoted at.
 *
 * <p><b>Why this guard exists.</b> 2026-10-31: eleven files had no such rule at all, so the engine ran their skills on
 * row 1 while their rules restated the Lv10 cells the documents quote - two number systems in one file. Worse, a file may
 * pin SOME slots: 1215's convention raises the Talent only, so reading its Ultimate row returns the Lv1 value (measured:
 * 0.15 where the document says 0.2). A rule may only read a slot the convention actually pins, and the fix for that is
 * per file, not per rule.
 *
 * <p>The invariant this pins is the one that can be checked without guessing: every file must carry an always-on
 * {@code RAISE_SKILL_LEVEL} rule (an Eidolon's +2 is not a convention), and the per-file coverage is printed so a partial
 * convention is visible instead of surprising.
 */
public class LevelConventionCoverageTest {

    @Test
    public void everyFilePinsAtLeastOneSlot() throws Exception {
        List<String> uncovered = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        int files = 0;

        List<Path> paths;
        try (var stream = Files.list(Path.of("src/main/resources/game/characters"))) {
            paths = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path path : paths) {
            String cid = path.getFileName().toString().replace(".json", "");
            JsonObject character;
            try {
                character = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException notAnObject) {
                continue;
            }
            JsonElement rules = character.get("rules");
            if (rules == null || !rules.isJsonArray()) {
                continue;
            }
            files++;
            Set<String> covered = new LinkedHashSet<>();
            for (JsonElement element : rules.getAsJsonArray()) {
                JsonObject rule = element.getAsJsonObject();
                // An Eidolon's +2 is not a convention: it depends on the rank, so it cannot say what the file quoted.
                if (rule.has("min_eidolon") || rule.has("max_eidolon")) {
                    continue;
                }
                if (!rule.has("on") || !"BATTLE_START".equals(rule.get("on").getAsString())) {
                    continue;
                }
                JsonElement doList = rule.get("do");
                if (doList == null || !doList.isJsonArray()) {
                    continue;
                }
                for (JsonElement effect : doList.getAsJsonArray()) {
                    JsonObject spec = effect.getAsJsonObject();
                    if (!spec.has("op") || !"RAISE_SKILL_LEVEL".equals(spec.get("op").getAsString())) {
                        continue;
                    }
                    covered.addAll(slotsOf(spec));
                }
            }
            if (covered.isEmpty()) {
                uncovered.add(cid);
            } else if (covered.size() < 4) {
                partial.add(cid + " [" + String.join("/", covered) + "]");
            }
        }
        System.out.println("[level-convention] files=" + files + " partial=" + partial.size() + " none=" + uncovered.size());
        System.out.println("[level-convention] partial: " + String.join(", ", partial));
        Assertions.assertTrue(files > 90, "precondition: the scan must see the character files");
        Assertions.assertTrue(uncovered.isEmpty(),
                "a character file must state which level its numbers were quoted at (an always-on BATTLE_START "
                        + "RAISE_SKILL_LEVEL rule); these do not: " + String.join(", ", uncovered));
    }

    /** The slots a {@code RAISE_SKILL_LEVEL} names - both spellings, {@code skill} and the {@code skills} list. */
    private static List<String> slotsOf(JsonObject effect) {
        List<String> slots = new ArrayList<>();
        JsonElement one = effect.get("skill");
        if (one != null && one.isJsonPrimitive()) {
            slots.add(one.getAsString());
        }
        JsonElement many = effect.get("skills");
        if (many != null && many.isJsonArray()) {
            JsonArray array = many.getAsJsonArray();
            for (JsonElement slot : array) {
                if (slot.isJsonPrimitive()) {
                    slots.add(slot.getAsString());
                }
            }
        }
        return slots;
    }
}