package com.laosun.aluminium.test;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Within ONE content file, two rules may not be the same sentence written twice.
 *
 * <p>⚠ Why: round 71 added `aura_party_damage_up` to 1415.json, whose file ALREADY had `talent_party_damage` with the same event,
 * the same (empty) conditions and the same effect; it cost rounds 72-74 to find, only because a mutation refused to move.
 *
 * <p>⚠ And why it prints: round 74's version passed while that duplicate existed. A guard must show its own work -- how many files
 * and rules it saw, and a couple of the signatures it built -- so "it did not fire" can be told apart from "it saw nothing".
 */
public class ContentDuplicateGuardTest {
    private static final Path ROOT = Path.of("src", "main", "resources").toAbsolutePath();
    private static final List<String> DIRS = List.of("characters", "light_cones", "relic_sets", "memosprites");
    /** `op` plus every operand that names WHAT is affected -- the parts that make two effects the same effect. */
    private static final List<String> OPERANDS =
            List.of("attribute", "buff", "resource", "rule", "state", "scale", "target", "permanent", "turns",
                    "percent", "amount", "crit_rate", "crit_damage", "per_stack", "max_stacks");

    @Test
    public void noFileStatesTheSameRuleTwice() throws IOException {
        System.out.println("[dup] root=" + ROOT + " exists=" + Files.isDirectory(ROOT));
        List<String> duplicates = new ArrayList<>();
        int files = 0;
        int rules = 0;
        List<String> samples = new ArrayList<>();
        for (String dir : DIRS) {
            Path start = ROOT.resolve(dir);
            if (!Files.isDirectory(start)) {
                System.out.println("[dup] " + dir + ": not a directory");
                continue;
            }
            try (Stream<Path> paths = Files.walk(start)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    if (path.getFileName().toString().startsWith("_")) {
                        continue;
                    }
                    files++;
                    List<String[]> entries = new ArrayList<>();
                    collect(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)), "", entries);
                    Map<String, String> seen = new LinkedHashMap<>();
                    Map<String, String> ids = new LinkedHashMap<>();
                    for (String[] entry : entries) {
                        rules++;
                        JsonObject rule = JsonParser.parseString(entry[1]).getAsJsonObject();
                        String signature = entry[0] + " :: " + signatureOf(rule);
                        if (samples.size() < 3) {
                            samples.add(path.getFileName() + " " + signature);
                        }
                        String previous = seen.put(signature, String.valueOf(rule.get("id")));
                        if (previous != null) {
                            duplicates.add(path + " [" + signature + "] ids " + previous + " / " + rule.get("id"));
                        }
                        // \u26a0 The sibling fault: the same id twice in one file is a copy-paste that survived, and it makes
                        // every later reference to that id ambiguous.
                        JsonElement id = rule.get("id");
                        if (id != null && !id.isJsonNull()) {
                            // \u26a0 Scoped by the rank/piece key: per-rank content reuses the rule id across ranks on purpose
                            // (each rank states its own numbers), so only a repeat INSIDE one scope is the copy-paste fault.
                            String earlier = ids.put(entry[0] + "\u0000" + id.getAsString(), entry[0]);
                            if (earlier != null) {
                                duplicates.add(path + " reuses id \"" + id.getAsString() + "\" twice inside scope \""
                                        + entry[0] + "\"");
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[dup] files=" + files + " rules=" + rules);
        for (String sample : samples) {
            System.out.println("[dup] sample: " + sample);
        }
        Assertions.assertTrue(files > 20 && rules > 100,
                "precondition: the walk must see the content (files=" + files + " rules=" + rules + ")");
        Assertions.assertTrue(duplicates.isEmpty(),
                "one sentence written twice (the round-71 mistake), " + duplicates.size() + " case(s): " + duplicates);
    }

    /** Each rule of a file as {scope, json}; the scope is the rank/piece key it lives under, "" for a bare list. */
    private static void collect(JsonElement element, String scope, List<String[]> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            // \u26a0 NOT the index: appending "#0", "#1", ... made every rule's scope unique, so no duplicate could ever collide
            // and the guard passed with a byte-identical copy in the file. Position is not meaning.
            for (JsonElement child : element.getAsJsonArray()) {
                collect(child, scope, out);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("on") && object.has("do")) {
            out.add(new String[] {scope, object.toString()});
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"resources".equals(entry.getKey())) {
                collect(entry.getValue(), scope + "/" + entry.getKey(), out);
            }
        }
    }

    private static String signatureOf(JsonObject rule) {
        StringBuilder out = new StringBuilder(String.valueOf(rule.get("on")));
        JsonElement when = rule.get("when");
        if (when != null && when.isJsonArray()) {
            JsonArray array = when.getAsJsonArray();
            List<String> conditions = new ArrayList<>();
            for (JsonElement condition : array) {
                conditions.add(condition.getAsString());
            }
            Collections.sort(conditions);
            out.append(" when=").append(conditions);
        }
        JsonElement effects = rule.get("do");
        if (effects != null && effects.isJsonArray()) {
            for (JsonElement effect : effects.getAsJsonArray()) {
                JsonObject spec = effect.getAsJsonObject();
                out.append(" | ").append(spec.get("op"));
                for (String operand : OPERANDS) {
                    if (spec.has(operand)) {
                        out.append(' ').append(operand).append('=').append(spec.get(operand));
                    }
                }
            }
        }
        return out.toString();
    }
}
