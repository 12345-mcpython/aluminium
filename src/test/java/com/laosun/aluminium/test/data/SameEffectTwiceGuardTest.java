package com.laosun.aluminium.test.data;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The same numeric effect written into <b>two different rules</b> of one file.
 *
 * <p><b>What this catches that the other guards do not.</b> {@code ContentDuplicateGuardTest} compares whole rules, so it
 * misses a clause that was copied into a <em>different</em> rule — which is exactly how 1203's Eidolon 1 ended up in both
 * the talent and the technique, and how 1412's Eidolon 1 ended up in the Skill. Those copies carry the right number, the
 * right target and the right duration, so no number-reading judge can see them; only the shape of "this identical effect
 * is stated twice in one file" can.
 *
 * <p><b>Why a baseline instead of zero.</b> Fourteen shapes in the shipped corpus are <em>legitimate</em>: one effect with
 * two trigger events (break vs kill), one aura stated once for battle start and once for late arrivals, or two rules that
 * both read the same live counter. The baseline records them by name so a NEW duplicate fails while the known ones stay
 * documented.
 */
public class SameEffectTwiceGuardTest {

    private static final Path ROOT = Path.of("src/main/resources/game");
    private static final List<String> DIRS = List.of("characters", "lightcones", "relics");

    /**
     * The shapes that are legitimate, by "file :: effect" - measured on 2026-11-01 and reviewed one by one (see
     * this file's BASELINE list, one reason each).
     */
    private static final Map<String, Integer> BASELINE = Map.ofEntries(
            Map.entry("1217.json :: MODIFY_ATTR|SPEED|0.12|all_allies|-|true", 2),
            Map.entry("1301.json :: MODIFY_DAMAGE_TAKEN||0.12|all_enemies|2|false", 2),
            Map.entry("1314.json :: MODIFY_ATTR|CRIT_ATTACK|0.024|self|-|true", 2),
            Map.entry("1314.json :: MODIFY_ATTR|ATTACK|0.005|self|-|true", 3),
            Map.entry("1315.json :: MODIFY_ATTR|BREAKING_EFFECT|0.3|self|2|false", 2),
            Map.entry("1321.json :: MODIFY_ATTR|BREAKING_EFFECT|0.24|other_allies|3|false", 2),
            Map.entry("1408.json :: MODIFY_ATTR|ATTACK|0.5|self|-|true", 2),
            Map.entry("1408.json :: MODIFY_ATTR|CRIT_ATTACK|0.06|self|-|true", 6),
            Map.entry("1409.json :: MODIFY_ATTR|DAMAGE_PENETRATION|0.2|all_allies|-|true", 2),
            Map.entry("1504.json :: MODIFY_ATTR|CRIT_ATTACK|0.8|self|-|true", 2),
            Map.entry("1506.json :: MODIFY_DAMAGE_TAKEN||0.2|all_enemies|-|true", 2),
            Map.entry("1512.json :: MODIFY_ATTR|ALL_DAMAGE_TYPE_BOOST|0.028|actor|1|false", 2),
            Map.entry("8001.json :: MODIFY_ATTR|ATTACK|0.2|self|-|true", 2),
            Map.entry("8002.json :: MODIFY_ATTR|ATTACK|0.2|self|-|true", 2));

    @Test
    public void noFileStatesTheSameNumericEffectInTwoRules() throws IOException {
        Map<String, Set<String>> shapes = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        for (String dir : DIRS) {
            Path start = ROOT.resolve(dir);
            if (!Files.isDirectory(start)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> !p.getFileName().toString().startsWith("_"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        for (Path path : files) {
            List<JsonObject> rules = new ArrayList<>();
            collect(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)), rules);
            Map<String, Set<String>> byEffect = new LinkedHashMap<>();
            for (JsonObject rule : rules) {
                JsonElement effects = rule.get("do");
                if (effects == null || !effects.isJsonArray()) {
                    continue;
                }
                for (JsonElement effect : effects.getAsJsonArray()) {
                    JsonObject spec = effect.getAsJsonObject();
                    String op = spec.has("op") ? spec.get("op").getAsString() : "";
                    if (!"MODIFY_ATTR".equals(op) && !"MODIFY_DAMAGE_TAKEN".equals(op)) {
                        continue;
                    }
                    if (!spec.has("percent") || spec.get("percent").isJsonNull()) {
                        continue;
                    }
                    String key = op + "|" + text(spec, "attribute") + "|" + spec.get("percent").getAsString()
                            + "|" + target(spec) + "|" + text(spec, "turns", "-")
                            + "|" + (spec.has("permanent") && spec.get("permanent").getAsBoolean());
                    byEffect.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(text(rule, "id"));
                }
            }
            for (Map.Entry<String, Set<String>> entry : byEffect.entrySet()) {
                if (entry.getValue().size() > 1) {
                    // The BASELINE is keyed by "file :: effect"; the rule ids are only for the failure message.
                    shapes.put(path.getFileName() + " :: " + entry.getKey(), entry.getValue());
                }
            }
        }
        // A shape is expected only at its recorded COUNT: a third copy of a known shape is still a new copy-paste.
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : shapes.entrySet()) {
            Integer expected = BASELINE.get(entry.getKey());
            if (expected == null) {
                unknown.add(entry.getKey() + " <- " + String.join(", ", entry.getValue()));
            } else if (expected != entry.getValue().size()) {
                unknown.add(entry.getKey() + " (was " + expected + " rules, now " + entry.getValue().size()
                        + ": " + String.join(", ", entry.getValue()) + ")");
            }
        }
        System.out.println("[same-effect] shapes=" + shapes.size() + " baseline=" + BASELINE.size()
                + " new=" + unknown.size());
        Assertions.assertTrue(unknown.isEmpty(),
                "an identical numeric effect is stated in two rules of one file (a clause copied into the wrong rule - "
                        + "see 1203 and 1412, fixed 2026-11-01). New or changed: " + unknown
                        + ". If the shape is legitimate (one effect, two triggers), add it to BASELINE with a reason.");
    }

    private static void collect(JsonElement element, List<JsonObject> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, out));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("on") && object.has("do")) {
            out.add(object);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"resources".equals(entry.getKey())) {
                collect(entry.getValue(), out);
            }
        }
    }

    private static String text(JsonObject object, String key) {
        return text(object, key, "");
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static String target(JsonObject spec) {
        return spec.has("target") && !spec.get("target").isJsonNull() ? spec.get("target").getAsString() : "self";
    }
}