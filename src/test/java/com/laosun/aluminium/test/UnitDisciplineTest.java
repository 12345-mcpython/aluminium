package com.laosun.aluminium.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The unit mistake that cost rounds 158-160, guarded.
 *
 * <p>`TriggerTable` resolves its numeric variables; {@code target_hp_percent} and {@code hp_percent} yield a FRACTION (the engine's own
 * comment for the latter spells {@code hp_percent <= 0.5}). Herta's file compared {@code target_hp_percent >= 50}, so the condition was
 * false for every damage instance and the rule was silently dead — which read as "BOOST_DAMAGE does nothing" for two rounds.
 *
 * <p>This test reads every shipped character file and fails on:
 * <ul>
 *   <li>a fraction-valued variable compared against a literal greater than 1 (the exact mistake);</li>
 *   <li>a numeric variable the engine does not resolve (a typo would be silently dead in the same way).</li>
 * </ul>
 */
public class UnitDisciplineTest {
    /** {@code TriggerTable}'s numeric-variable switch (set at "hit_count" ... "from_skill_id"). */
    private static final Set<String> KNOWN = Set.of(
            "hit_count", "hp_percent", "target_hp_percent", "target_debuff_count",
            "self_summon_count", "target_summon_count", "self_max_energy", "from_skill_id",
            "target_hp_percent_before", "enemy_count", "ally_count");   // added with the crossing variable (round 181)

    /** Of those, the ones that resolve to 0..1 rather than a count. */
    private static final Set<String> FRACTION = Set.of("hp_percent", "target_hp_percent", "target_hp_percent_before");

    private static final Pattern COMPARISON =
            Pattern.compile("^\\s*([a-z_]+)\\s*(>=|<=|>|<|==)\\s*(-?[0-9.]+)\\s*$");

    @Test
    public void noShippedConditionComparesAFractionAgainstAFractionlessNumber() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : characterFiles()) {
            // Most character files are a rule ARRAY; 1003's root is an object carrying `resources` as well, so the rules are taken
            // from whichever shape the file uses (the guard found this on its first run).
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonArray rules = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().has("rules") && root.getAsJsonObject().get("rules").isJsonArray()
                            ? root.getAsJsonObject().getAsJsonArray("rules")
                            : new JsonArray();
            for (JsonElement element : rules) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject rule = element.getAsJsonObject();
                if (!rule.has("when") || !rule.get("when").isJsonArray()) {
                    continue;
                }
                String id = rule.has("id") ? rule.get("id").getAsString() : "(no id)";
                for (JsonElement condition : rule.getAsJsonArray("when")) {
                    Matcher matcher = COMPARISON.matcher(condition.getAsString());
                    if (!matcher.matches()) {
                        continue;
                    }
                    String variable = matcher.group(1);
                    double literal = Double.parseDouble(matcher.group(3));
                    String where = file.getFileName() + " " + id + ": \"" + condition.getAsString() + "\"";
                    if (!KNOWN.contains(variable)) {
                        problems.add("unknown numeric variable -> " + where);
                    } else if (FRACTION.contains(variable) && literal > 1) {
                        problems.add("fraction compared against a literal above 1 -> " + where);
                    }
                }
            }
        }
        Assertions.assertTrue(problems.isEmpty(),
                "content must state its numbers in the engine's own units (see UnitDisciplineTest's javadoc): " + problems);
    }

    private static List<Path> characterFiles() throws IOException {
        Path dir = Path.of("src", "main", "resources", "characters");
        Assertions.assertTrue(Files.isDirectory(dir), "character directory must exist: " + dir.toAbsolutePath());
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
