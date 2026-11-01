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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A <b>star-soul (星魂) effect</b> written inside a rule that is not a star-soul rule.
 *
 * <p><b>The class this catches.</b> 1203's Eidolon 1 boost and Eidolon 4 weaken were written into the talent <em>and</em>
 * into the technique, and 1412's Eidolon 1 defence ignore was written into the Skill. Every one of those copies carried
 * the right number, so no number-reading judge could see them; the tell is structural - an effect gated by
 * {@code min_eidolon} / {@code max_eidolon} sitting inside a rule whose id does not start with {@code eidolon}.
 *
 * <p><b>Why a baseline.</b> Five rules legitimately carry such effects: a transformation that grows with Eidolons, a
 * resource clause that only exists at rank 1, and the zone-opening rules. The baseline records them by name and by how many
 * gated effects they carry, so a NEW one fails while the reviewed ones stay documented.
 */
public class EidolonClauseInNonEidolonRuleGuardTest {

    private static final Path ROOT = Path.of("src/main/resources/game");
    private static final List<String> DIRS = List.of("characters", "lightcones", "relics");

    /** Reviewed on 2026-11-01; see {@code docs/MODIFIER-COLLISION-AUDIT.md}, "另一类扫描". */
    private static final Map<String, Integer> BASELINE = Map.ofEntries(
            Map.entry("1203.json :: talent_zone_opens_at_two_stacks", 1),
            Map.entry("1408.json :: ult_transformation", 2),
            Map.entry("1505.json :: p1505_energy_sync", 1),
            Map.entry("1506.json :: p1506_ult_enters_godmode", 2),
            Map.entry("1506.json :: p1506_leaving_godmode_clears_hidden", 2));

    @Test
    public void noStarSoulClauseHidesInAPlainRule() throws IOException {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (String dir : DIRS) {
            Path start = ROOT.resolve(dir);
            if (!Files.isDirectory(start)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> !p.getFileName().toString().startsWith("_"))
                        .sorted()
                        .forEach(files::add);
            }
            for (Path path : files) {
                List<JsonObject> rules = new ArrayList<>();
                collect(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)), rules);
                for (JsonObject rule : rules) {
                    String id = rule.has("id") ? rule.get("id").getAsString() : "";
                    if (id.startsWith("eidolon")) {
                        continue;
                    }
                    int gated = 0;
                    JsonElement effects = rule.get("do");
                    if (effects != null && effects.isJsonArray()) {
                        for (JsonElement effect : effects.getAsJsonArray()) {
                            JsonObject spec = effect.getAsJsonObject();
                            if (spec.has("min_eidolon") || spec.has("max_eidolon")) {
                                gated++;
                            }
                        }
                    }
                    if (gated > 0) {
                        found.put(path.getFileName() + " :: " + id, gated);
                    }
                }
            }
        }
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : found.entrySet()) {
            Integer expected = BASELINE.get(entry.getKey());
            if (expected == null) {
                unknown.add(entry.getKey() + " (" + entry.getValue() + " gated effect(s))");
            } else if (!expected.equals(entry.getValue())) {
                unknown.add(entry.getKey() + " (was " + expected + ", now " + entry.getValue() + ")");
            }
        }
        System.out.println("[eidolon-clause] rules=" + found.size() + " baseline=" + BASELINE.size()
                + " new=" + unknown.size());
        Assertions.assertTrue(unknown.isEmpty(),
                "a star-soul effect sits inside a rule that is not a star-soul rule - the clause was copied into the "
                        + "wrong rule (see 1203 and 1412, fixed 2026-11-01). New or changed: " + unknown
                        + ". If the rule legitimately grows with Eidolons, add it to BASELINE with a reason.");
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
}