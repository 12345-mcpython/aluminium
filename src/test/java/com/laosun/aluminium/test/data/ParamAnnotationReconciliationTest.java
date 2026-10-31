package com.laosun.aluminium.test.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.Skill;
import com.laosun.aluminium.enums.SkillType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A rule's structured {@code source} states the parameter row its prose quotes.
 *
 * <pre>{@code
 * "source": { "text": "…", "skill": "SKILL", "row": 10,
 *             "params": { "#1": 0.57, "#2": 3, "#3": 0.3, "#4": 760, "#5": 5 } }
 * }</pre>
 *
 * <p>The test resolves the row through {@link Constant#SKILLS} and compares every {@code #N} value, so a stale row is a
 * red test rather than prose nobody re-checks. The old {@code _params} annotation is no longer accepted.
 */
public class ParamAnnotationReconciliationTest {

    private static final double EPS = 1e-9;

    @Test
    public void everyAnnotatedParamMatchesTheSkillRow() throws Exception {
        Path root = Path.of("src", "main", "resources", "game", "characters");
        Assertions.assertTrue(Files.isDirectory(root), "the character directory must exist: " + root.toAbsolutePath());

        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                int cid = Integer.parseInt(file.getFileName().toString().replace(".json", ""));
                for (JsonObject rule : rules(file)) {
                    JsonObject params = sourceParams(rule);
                    if (params == null) {
                        continue;
                    }
                    checked++;
                    check(cid, rule, params, problems);
                }
            }
        }
        Assertions.assertTrue(checked >= 5,
                "expected at least the five 1001 parameter rules (_params or a structured source), found " + checked);
        Assertions.assertEquals(List.of(), problems, "annotated params disagree with the skill table:\n  "
                + String.join("\n  ", problems));
    }

    /**
     * Reads the parameter row from the structured {@code source}; a plain string source has none.
     */
    private static JsonObject sourceParams(JsonObject rule) {
        JsonElement source = rule.get("source");
        if (source != null && source.isJsonObject()) {
            JsonObject object = source.getAsJsonObject();
            if (object.has("skill") && object.has("row") && object.has("params")
                    && object.get("params").isJsonObject()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("skill", object.get("skill"));
                wrapper.add("row", object.get("row"));
                wrapper.add("values", object.get("params"));
                return wrapper;
            }
        }
        return null;
    }

    private static void check(int cid, JsonObject rule, JsonObject params, List<String> problems) {
        String id = rule.has("id") ? rule.get("id").getAsString() : "(no id)";
        String where = cid + " / " + id;
        try {
            SkillType type = SkillType.valueOf(params.get("skill").getAsString().trim().toUpperCase(Locale.ROOT));
            Integer slot = Constant.SKILL_SLOT.get(type);
            Assertions.assertNotNull(slot, where + ": no data slot for " + type);
            Skill skill = Constant.SKILLS.get(cid) == null ? null : Constant.SKILLS.get(cid).get(slot);
            Assertions.assertNotNull(skill, where + ": no skill data for slot " + slot);
            int row = params.get("row").getAsInt();
            List<List<Double>> rows = skill.paramList();
            Assertions.assertTrue(row >= 1 && row <= rows.size(), where + ": row " + row + " outside 1.." + rows.size());
            List<Double> table = rows.get(row - 1);
            for (var entry : params.getAsJsonObject("values").entrySet()) {
                String key = entry.getKey();
                Assertions.assertTrue(key.startsWith("#"), where + ": param key \"" + key + "\" must be #N");
                int index = Integer.parseInt(key.substring(1));
                Assertions.assertTrue(index >= 1 && index <= table.size(),
                        where + ": " + key + " outside row of " + table.size() + " values");
                double expected = table.get(index - 1);
                double actual = entry.getValue().getAsDouble();
                if (Math.abs(expected - actual) > EPS) {
                    problems.add(where + ": " + key + " annotated " + actual + " but row " + row + " says " + expected);
                }
            }
        } catch (RuntimeException malformed) {
            problems.add(where + ": " + malformed.getMessage());
        }
    }

    private static List<JsonObject> rules(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().getAsJsonArray("rules");
            List<JsonObject> rules = new ArrayList<>();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    rules.add(element.getAsJsonObject());
                }
            }
            return rules;
        }
    }
}
