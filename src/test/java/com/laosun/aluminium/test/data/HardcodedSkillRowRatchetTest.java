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
import java.util.List;
import java.util.Set;

/**
 * A RATCHET on "the number in the rule is a row of the skill table, typed by hand".
 *
 * <p><b>Why.</b> The corpus states magnitudes as literals that equal the skill row at <b>Lv10</b>, and pins every slot to
 * Lv10 through its {@code level_convention} rule. That is exact only while the slot stays at 10: an Eidolon that adds
 * levels (「战技等级+2」) moves the row and the literal does not follow. Measured 2026-10-31: 219 rules read this way.
 *
 * <p>Fixing all of them is a corpus-wide job, so this test does the one thing that stops the bleeding: it fails when the
 * count <b>grows</b>. A new rule should read the row ({@code percent_from_skill_param}, {@code skill} + {@code damage_param},
 * or a {@code value} provider) instead of restating its Lv10 cell.
 */
public class HardcodedSkillRowRatchetTest {

    /**
     * The measured count on 2026-10-31. Counters are not counted at all (see MAGNITUDE_OPS): 12 of the original
     * entries were "gain 1 point" style counts that only coincided with a Lv10 cell. ⚠ A file whose `level_convention` pins only SOME slots still runs the others at
     * Lv1, so a literal there is exact and a row read would silently drop it (measured on 1215: its convention raises the
     * TALENT only, so the Ultimate numbers must stay literal). Counted over the four NAMED slots only (8/9/10/12 are the enhanced rows and
     * no SkillType reaches them, so a number matching one cannot be replaced by a row read at all), after four
     * corrections: 1009 reads its four rows instead of restating them, and
     * numbers a character TRACE states are no longer reported (52 of the original 219 were trace constants, which are not
     * leveled and therefore exact). Lowering it is progress; raising it is the fault this test exists to catch.
     */
    private static final int BASELINE = 57;

    /**
     * The ops whose magnitude is a <b>share of the skill row</b>. Counters are deliberately absent: "gain 1 point of
     * 【气氛值】" or "remove 1 stack" is a COUNT, and a count that happens to equal a Lv10 cell (1 and 2 are everywhere)
     * is a coincidence, not a frozen row - reported, it would push a future reader to bind the counter to a column.
     */
    private static final Set<String> MAGNITUDE_OPS = Set.of(
            "DAMAGE", "HEAL", "SHIELD", "APPLY_DOT", "APPLY_REGEN", "MODIFY_ATTR", "MODIFY_DAMAGE_TAKEN",
            "BOOST_DAMAGE", "TICK_DOT", "APPLY_CONTROL");

    @Test
    public void noMoreRulesFreezeAlevelVaryingRow() throws Exception {
        JsonObject skills = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/game/data/skills.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        // A trace's numbers are NOT leveled, so a rule that restates one is exact - they must count as "from the data"
        // or every trace constant that happens to equal a skill cell would be reported (1101's 阵地 is one).
        JsonObject traces = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/game/data/skill_traces.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> suspects = new ArrayList<>();

        List<Path> files;
        try (var paths = Files.list(Path.of("src/main/resources/game/characters"))) {
            files = paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        for (Path file : files) {
            String cid = file.getFileName().toString().replace(".json", "");
            JsonElement rows = skills.get(cid);
            if (rows == null || !rows.isJsonObject()) {
                continue;
            }
            JsonObject character;
            try {
                character = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException notAnObject) {
                continue;
            }
            JsonElement rules = character.get("rules");
            if (rules == null || !rules.isJsonArray()) {
                continue;
            }
            List<Column> moving = movingColumns(rows.getAsJsonObject());
            if (moving.isEmpty()) {
                continue;
            }
            for (JsonElement element : rules.getAsJsonArray()) {
                JsonObject rule = element.getAsJsonObject();
                JsonElement doList = rule.get("do");
                if (doList == null || !doList.isJsonArray()) {
                    continue;
                }
                for (JsonElement effectElement : doList.getAsJsonArray()) {
                    JsonObject effect = effectElement.getAsJsonObject();
                    String op = effect.has("op") ? effect.get("op").getAsString() : "";
                    if (!MAGNITUDE_OPS.contains(op) || readsTheRow(effect)) {
                        continue;
                    }
                    for (String field : List.of("percent", "amount")) {
                        JsonElement value = effect.get(field);
                        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                            continue;
                        }
                        double literal = value.getAsDouble();
                        for (Column column : moving) {
                            if (column.picksTheLv10Row(literal) && !traceStates(traces, cid, literal)) {
                                suspects.add(cid + " " + (rule.has("id") ? rule.get("id").getAsString() : "?")
                                        + " " + op + " " + field + "=" + literal);
                                break;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("[skill-row-ratchet] suspects=" + suspects.size() + " baseline=" + BASELINE);
        Assertions.assertTrue(suspects.size() <= BASELINE,
                "a rule froze a level-varying skill row: read the row instead (percent_from_skill_param / skill + "
                        + "damage_param / a value provider). Count grew from " + BASELINE + " to " + suspects.size()
                        + "; the new ones are among: " + String.join(" | ", suspects.subList(Math.min(BASELINE, suspects.size()),
                        Math.min(suspects.size(), BASELINE + 10))));
    }

    /** Whether one of this character's TRACES states this number (traces are not leveled, so restating one is exact). */
    private static boolean traceStates(JsonObject traces, String cid, double literal) {
        JsonElement entries = traces.get(cid);
        if (entries == null || !entries.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : entries.getAsJsonArray()) {
            if (!entry.isJsonObject() || !entry.getAsJsonObject().has("param")) {
                continue;
            }
            JsonElement param = entry.getAsJsonObject().get("param");
            if (!param.isJsonArray()) {
                continue;
            }
            for (JsonElement value : param.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                        && Math.abs(value.getAsDouble() - literal) < 1e-9) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A field that already says where its number comes from needs no change. */
    private static boolean readsTheRow(JsonObject effect) {
        return effect.has("skill") || effect.has("damage_param") || effect.has("percent_from_skill_param")
                || effect.has("amount_from_skill_param") || effect.has("value");
    }

    private static List<Column> movingColumns(JsonObject rows) {
        List<Column> columns = new ArrayList<>();
        for (var entry : rows.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            // Only the four slots a rule can NAME: 8/9/10/12 are the enhanced rows, and there is no SkillType that
            // reaches them, so a number that matches one cannot be replaced by a row-read share at all.
            if (!List.of("1", "2", "3", "4").contains(entry.getKey())) {
                continue;
            }
            JsonElement list = entry.getValue().getAsJsonObject().get("param_list");
            if (list == null || !list.isJsonArray()) {
                continue;
            }
            JsonArray table = list.getAsJsonArray();
            if (table.size() < 3 || !table.get(0).isJsonArray()) {
                continue;
            }
            int width = table.get(0).getAsJsonArray().size();
            for (int col = 0; col < width; col++) {
                List<Double> values = new ArrayList<>();
                for (JsonElement row : table) {
                    if (!row.isJsonArray() || row.getAsJsonArray().size() <= col) {
                        continue;
                    }
                    JsonElement cell = row.getAsJsonArray().get(col);
                    if (cell.isJsonPrimitive() && cell.getAsJsonPrimitive().isNumber()) {
                        values.add(cell.getAsDouble());
                    }
                }
                if (values.size() >= 3) {
                    columns.add(new Column(entry.getKey(), col + 1, values));
                }
            }
        }
        return columns;
    }

    /** One skill-table column, with the three rows that decide whether a literal "picked" Lv10. */
    private record Column(String slot, int number, List<Double> values) {

        boolean picksTheLv10Row(double literal) {
            double low = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double high = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            if (high - low < 1e-9) {
                return false;                        // a constant column is not the fault
            }
            double at10 = values.get(Math.min(9, values.size() - 1));
            double first = values.get(0);
            double last = values.get(values.size() - 1);
            return Math.abs(at10 - literal) < 1e-9
                    && Math.abs(first - literal) > 1e-9
                    && Math.abs(last - literal) > 1e-9;
        }
    }
}