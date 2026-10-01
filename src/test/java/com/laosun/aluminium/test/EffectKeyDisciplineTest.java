package com.laosun.aluminium.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.beans.EffectSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every key an effect object uses must be one the engine actually reads.
 *
 * <p>Gson maps a field by its {@code @SerializedName} when it has one and by the JAVA field name when it does not --
 * so the two conventions coexist (`op`, `per_stack`, `cap_amount` next to `amountFromEvent`, `amountPercent`). An
 * unknown key is not an error: it is dropped. That made 1312's `amount_from_event` / `amount_percent` a rule that
 * loaded, passed the whole suite and both demo gates, and did nothing (measured in rounds 668-683, about five rounds).
 *
 * <p>The known set is read off {@link EffectSpec} by reflection, so adding a field extends the guard automatically.
 */
public class EffectKeyDisciplineTest {
    /** `note` belongs to a RULE; an effect that repeats one is dead text, but harmless and already shipped. */
    private static final Set<String> ALSO_ALLOWED = Set.of("note");

    private static Set<String> knownKeys() {
        Set<String> known = new HashSet<>();
        for (Field f : EffectSpec.class.getDeclaredFields()) {
            SerializedName sn = f.getAnnotation(SerializedName.class);
            known.add(sn == null ? f.getName() : sn.value());
        }
        known.addAll(ALSO_ALLOWED);
        return known;
    }

    private static void walk(JsonElement el, Set<String> known, Map<String, Set<String>> unknown, String file) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("op") && o.get("op").isJsonPrimitive()) {
                for (String key : o.keySet()) {
                    if (!known.contains(key)) {
                        unknown.computeIfAbsent(key, k -> new HashSet<>()).add(file);
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                walk(e.getValue(), known, unknown, file);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                walk(e, known, unknown, file);
            }
        }
    }

    @Test
    public void everyEffectKeyIsOneTheEngineReads() throws Exception {
        Set<String> known = knownKeys();
        Assertions.assertTrue(known.size() > 30, "the reflected key set looks too small: " + known.size());
        Map<String, Set<String>> unknown = new TreeMap<>();
        int files = 0;
        for (String dir : List.of("characters", "light_cones", "relic_sets", "memosprites")) {
            Path root = Path.of("src", "main", "resources", dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(root)) {
                for (Path p : paths.filter(x -> x.toString().endsWith(".json")).toList()) {
                    if (p.getFileName().toString().startsWith("_")) {
                        continue;
                    }
                    files++;
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        walk(JsonParser.parseReader(r), known, unknown, p.getFileName().toString());
                    }
                }
            }
        }
        Assertions.assertTrue(files > 200, "expected the shipped content, found " + files + " files");
        Assertions.assertEquals(Map.of(), unknown,
                "these effect keys are not fields of EffectSpec, so Gson drops them silently: " + unknown);
        System.out.println("[keys] " + files + " files, " + known.size() + " known keys, 0 unknown");
    }
}
