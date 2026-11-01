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
 * An Eidolon rule's numbers must be the numbers its own character data states.
 *
 * <p><b>Why this guard exists.</b> Measured on 2026-10-31: three Eidolons written that day carried a number that appears
 * nowhere in the character data - 1006 rank 1 pays 7 energy per debuff (written 1), rank 6 raises damage 20% per debuff
 * (written 1%), and 1304 rank 6 gives 50% per shielded ally (written 0.5%). Each came from reading the prose instead of
 * the param column, and none failed a test: the tests had been written against the same wrong number.
 *
 * <p>The rule is narrow on purpose: a rule whose source names 星魂 may only use numbers that appear in that character's
 * own data (Eidolon params, or any skill row).
 */
public class EidolonNumbersComeFromTheDataTest {

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "percent", "amount", "turns", "times", "effect_percent", "effect_amount", "effect_turns");

    /**
     * Numbers that are known NOT to come from this character's param columns, kept visible instead of silently allowed.
     *
     * <p>Empty since 2026-10-31: the one entry it held (1403's rank 6, the hand-computed 1.4922 / 1.3122) now derives
     * its number from the Talent row through a `value` provider, so there is nothing frozen left to allow.
     */
    private static final Set<String> KNOWN_FROZEN_DERIVATIONS = Set.of();
    private static final double EPS = 1e-9;

    @Test
    public void everyEidolonNumberExistsInThatCharactersData() throws Exception {
        JsonObject eidolons = read(Path.of("src/main/resources/game/data/eidolons.json"));
        JsonObject skills = read(Path.of("src/main/resources/game/data/skills.json"));
        List<String> violations = new ArrayList<>();
        int rulesSeen = 0;

        List<Path> files;
        try (var paths = Files.list(Path.of("src/main/resources/game/characters"))) {
            files = paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path file : files) {
            String cid = file.getFileName().toString().replace(".json", "");
            JsonObject character;
            try {
                character = read(file);
            } catch (RuntimeException notAnObject) {
                continue;
            }
            JsonElement rules = character.get("rules");
            if (rules == null || !rules.isJsonArray()) {
                continue;
            }
            Set<Double> pool = pool(eidolons.get(cid), skills.get(cid));
            for (JsonElement element : rules.getAsJsonArray()) {
                JsonObject rule = element.getAsJsonObject();
                String source = rule.has("source") && rule.get("source").isJsonPrimitive()
                        ? rule.get("source").getAsString()
                        : "";
                if (!source.contains("星魂")) {
                    continue;
                }
                rulesSeen++;
                if (!rule.has("do") || !rule.get("do").isJsonArray()) {
                    continue;
                }
                for (JsonElement effect : rule.getAsJsonArray("do")) {
                    for (double number : numbersOf(effect.getAsJsonObject())) {
                        String key = cid + " " + (rule.has("id") ? rule.get("id").getAsString() : "?") + " " + number;
                        if (!inPool(number, pool) && !KNOWN_FROZEN_DERIVATIONS.contains(key)) {
                            violations.add(cid + " " + (rule.has("id") ? rule.get("id").getAsString() : "?") + ": " + number
                                    + " is in no param column of this character");
                        }
                    }
                }
            }
        }
        System.out.println("[eidolon-numbers] rules=" + rulesSeen + " violations=" + violations.size());
        Assertions.assertTrue(rulesSeen > 50, "precondition: the scan must see the Eidolon rules");
        Assertions.assertTrue(violations.isEmpty(),
                "an Eidolon states its own numbers - read the param column, not the prose; " + violations.size()
                        + " case(s): " + String.join(" | ", violations));
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<Double> pool(JsonElement eidolons, JsonElement skills) {
        Set<Double> pool = new LinkedHashSet<>();
        if (eidolons != null && eidolons.isJsonObject()) {
            for (var entry : eidolons.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement param = entry.getValue().getAsJsonObject().get("param");
                if (param != null && param.isJsonArray()) {
                    collect(param.getAsJsonArray(), pool);
                }
            }
        }
        if (skills != null && skills.isJsonObject()) {
            for (var entry : skills.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement list = entry.getValue().getAsJsonObject().get("param_list");
                if (list != null && list.isJsonArray()) {
                    collect(list.getAsJsonArray(), pool);
                }
            }
        }
        return pool;
    }

    private static void collect(JsonArray array, Set<Double> out) {
        for (JsonElement element : array) {
            if (element.isJsonArray()) {
                collect(element.getAsJsonArray(), out);
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                out.add(element.getAsDouble());
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                for (String piece : element.getAsString().trim().split(" ")) {
                    try {
                        out.add(Double.parseDouble(piece));
                    } catch (NumberFormatException ignored) {
                        // A non-numeric cell states no number.
                    }
                }
            }
        }
    }

    private static List<Double> numbersOf(JsonObject effect) {
        List<Double> numbers = new ArrayList<>();
        for (String field : NUMERIC_FIELDS) {
            JsonElement value = effect.get(field);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                numbers.add(value.getAsDouble());
            }
        }
        numbers.addAll(constsIn(effect.get("value")));
        return numbers;
    }

    private static List<Double> constsIn(JsonElement node) {
        List<Double> out = new ArrayList<>();
        if (node == null || node.isJsonNull()) {
            return out;
        }
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                out.addAll(constsIn(child));
            }
            return out;
        }
        if (!node.isJsonObject()) {
            return out;
        }
        JsonObject object = node.getAsJsonObject();
        if (object.has("type") && "const".equals(object.get("type").getAsString())
                && object.has("value") && object.get("value").isJsonPrimitive()) {
            out.add(object.get("value").getAsDouble());
        }
        for (var entry : object.entrySet()) {
            out.addAll(constsIn(entry.getValue()));
        }
        return out;
    }

    /**
     * Whether the data states this number, allowing for the two conventions content uses on purpose:
     *
     * <ul>
     *   <li><b>sign</b> - "受到的伤害降低 20%" is written {@code percent: -0.2} while the param says {@code 0.2};</li>
     *   <li><b>plus or minus one</b> - "终结技额外增加 1 次" states {@code param + 1} (1107 rank 6: param 2, three
     *       charges), and "上限降低至 6 层" states the param itself.</li>
     * </ul>
     *
     * <p>Everything else must be a number the character's own data contains - that is what catches a prose-derived
     * magnitude (0.5% written for a param of 0.5, or 1 energy written for a param of 7).
     */
    private static boolean inPool(double number, Set<Double> pool) {
        double magnitude = Math.abs(number);
        for (double candidate : pool) {
            double stated = Math.abs(candidate);
            if (Math.abs(stated - magnitude) < EPS
                    || Math.abs(stated + 1 - magnitude) < EPS
                    || Math.abs(stated - 1 - magnitude) < EPS) {
                return true;
            }
        }
        return false;
    }
}