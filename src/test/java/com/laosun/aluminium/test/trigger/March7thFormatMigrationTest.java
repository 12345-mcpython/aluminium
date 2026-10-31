package com.laosun.aluminium.test.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The first content file on the new trigger skeleton: 1001 March 7th.
 *
 * <p><b>Why this file is the sample.</b> It already carried the most annotation text and the most
 * {@code when_predicates}, and its {@code level_convention} was four identical {@code RAISE_SKILL_LEVEL} effects.
 * Migrating it pins the three shapes the loader gained ({@code _note}, predicate objects in {@code when}, and
 * {@code skills}) against a real, shipped rule table rather than only against fixtures.
 *
 * <p>⚠ This is a <b>format</b> judge: it does not re-measure the mechanics. Those are pinned by
 * {@code March7thKitTest} and the other content judges, which must keep passing unchanged.
 */
public class March7thFormatMigrationTest {

    private static final int MARCH_7TH = 1001;

    /**
     * The rule-level annotation is the underscore form, and it never becomes a bean field.
     */
    @Test
    public void theFileUsesAnnotationsInsteadOfLegacyNotes() {
        JsonArray rules = rules();
        Assertions.assertThrows(NoSuchFieldException.class, () -> TriggerSpec.class.getDeclaredField("note"),
                "notes are annotations, not runtime fields");
        int annotated = 0;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            Assertions.assertFalse(rule.has("note"),
                    "a migrated rule must use \"_note\", not the legacy \"note\": " + rule.get("id"));
            if (rule.has("_note")) {
                annotated++;
            }
        }
        Assertions.assertTrue(annotated > 0, "the sample file keeps its annotations");
    }

    /**
     * The counter rule is the first shipped rule whose {@code source} is the structured object; its parameter row
     * lives there instead of in a separate {@code _params} block.
     */
    @Test
    public void theCounterUsesAStructuredSource() {
        JsonObject counter = ruleById("counter");
        Assertions.assertFalse(counter.has("_params"), "the row lives in source, not in a second annotation");
        JsonObject source = counter.getAsJsonObject("source");
        Assertions.assertEquals("TALENT", source.get("skill").getAsString());
        Assertions.assertEquals(10, source.get("row").getAsInt());
        Assertions.assertEquals(1.0, source.getAsJsonObject("params").get("#1").getAsDouble());
        Assertions.assertEquals(2.0, source.getAsJsonObject("params").get("#2").getAsDouble());
        Assertions.assertFalse(source.get("text").getAsString().isBlank());
    }

    /**
     * All five parameter-bearing rules state the row in the structured source; the file no longer needs {@code _params}.
     */
    @Test
    public void parameterRowsLiveInSourceObjects() {
        int structured = 0;
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            Assertions.assertFalse(rule.has("_params"), "1001 has no _params: " + rule.get("id"));
            JsonElement source = rule.get("source");
            if (source != null && source.isJsonObject()
                    && source.getAsJsonObject().has("skill")
                    && source.getAsJsonObject().has("row")
                    && source.getAsJsonObject().has("params")) {
                structured++;
            }
        }
        Assertions.assertEquals(5, structured, "the five parameter rules use the structured source");
    }

    /**
     * The sample file also demonstrates structured status: every rule says what state it is in.
     */
    @Test
    public void everyRuleCarriesAStatus() {
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            Assertions.assertTrue(rule.has("_status"), "rule " + rule.get("id") + " has no _status");
            Assertions.assertEquals("implemented", rule.get("_status").getAsString(),
                    "1001 is the fully implemented sample: " + rule.get("id"));
        }
    }

    /**
     * Every structured condition moved from {@code when_predicates} into the one {@code when} array, and the engine
     * compiles it through the same path as the DSL strings.
     */
    @Test
    public void everyStructuredConditionLivesInWhen() {
        int objectEntries = 0;
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            Assertions.assertFalse(rule.has("when_predicates"),
                    "the sample migrated the old spelling: " + rule.get("id"));
            JsonElement when = rule.get("when");
            if (when == null || !when.isJsonArray()) {
                continue;
            }
            for (JsonElement condition : when.getAsJsonArray()) {
                if (condition.isJsonObject()) {
                    objectEntries++;
                }
            }
        }
        Assertions.assertTrue(objectEntries > 0, "the sample keeps the structured half");
        Assertions.assertFalse(TriggerTables.of(MARCH_7TH).isEmpty(), "and the table still loads");
    }

    /**
     * The shield's three-field magnitude became one {@code value} expression: DEFENCE x the Skill's #1 + the Skill's #4.
     */
    @Test
    public void theShieldUsesOneValueExpression() {
        JsonObject shield = ruleById("skill_shield").getAsJsonArray("do").get(0).getAsJsonObject();
        Assertions.assertTrue(shield.has("value"), "the new spelling is stated");
        Assertions.assertFalse(shield.has("scale") || shield.has("percent") || shield.has("amount")
                        || shield.has("percent_from") || shield.has("amount_from"),
                "no legacy magnitude field sits beside it: " + shield.keySet());

        var effect = TriggerTables.of(MARCH_7TH).rulesFor(TriggerEvent.SKILL_CAST).stream()
                .filter(rule -> "skill_shield".equals(rule.id()))
                .findFirst()
                .orElseThrow()
                .effects().getFirst();
        Assertions.assertNotNull(effect.getValue(), "the loader read the expression");
        Assertions.assertNull(effect.getScale(), "and not the old scale");
        Assertions.assertNull(effect.getPercent(), "nor the old share");
        Assertions.assertNull(effect.getAmount(), "nor the old constant");
    }

    /**
     * The level convention is one effect with a {@code skills} list, and the engine files the raise on every slot.
     */
    @Test
    public void theLevelConventionIsOneSkillsEffect() {
        JsonObject convention = ruleById("level_convention");
        JsonArray effects = convention.getAsJsonArray("do");
        Assertions.assertEquals(1, effects.size(), "four identical effects became one");
        JsonObject effect = effects.get(0).getAsJsonObject();
        Assertions.assertEquals("RAISE_SKILL_LEVEL", effect.get("op").getAsString());
        Assertions.assertFalse(effect.has("skill"), "the singular spelling is gone");

        List<String> slots = new ArrayList<>();
        effect.getAsJsonArray("skills").forEach(slot -> slots.add(slot.getAsString()));
        Assertions.assertEquals(List.of("COMMON", "SKILL", "ULTRA", "TALENT"), slots);
        Assertions.assertEquals(9, effect.get("amount").getAsInt());

        var compiled = TriggerTables.of(MARCH_7TH).rulesFor(TriggerEvent.BATTLE_START).stream()
                .filter(rule -> "level_convention".equals(rule.id()))
                .toList();
        Assertions.assertEquals(1, compiled.size(), "one compiled convention rule");
        var first = compiled.getFirst().effects().getFirst();
        Assertions.assertEquals(slots, first.getSkills(), "the loader read the list");
        Assertions.assertNull(first.getSkill(), "and not the singular field");
    }

    /**
     * 星魂 5 raises two slots by the same amount, so it uses the same shape.
     */
    @Test
    public void theEidolonWithOneAmountForTwoSlotsUsesSkills() {
        JsonObject eidolon = ruleById("eidolon_five_raises_the_skill_and_talent");
        JsonArray effects = eidolon.getAsJsonArray("do");
        Assertions.assertEquals(1, effects.size(), "two identical amounts became one effect");
        JsonObject effect = effects.get(0).getAsJsonObject();
        List<String> slots = new ArrayList<>();
        effect.getAsJsonArray("skills").forEach(slot -> slots.add(slot.getAsString()));
        Assertions.assertEquals(List.of("SKILL", "TALENT"), slots);
        Assertions.assertEquals(2, effect.get("amount").getAsInt());
    }

    private static JsonArray rules() {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1001.json");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("rules");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + file.toAbsolutePath(), e);
        }
    }

    private static JsonObject ruleById(String id) {
        for (JsonElement element : rules()) {
            JsonObject rule = element.getAsJsonObject();
            if (id.equals(rule.has("id") ? rule.get("id").getAsString() : "")) {
                return rule;
            }
        }
        throw new IllegalStateException("1001 has no rule with id " + id);
    }
}
