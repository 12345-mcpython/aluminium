package com.laosun.aluminium.test.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A rule that fires REPEATEDLY and re-attaches a PERMANENT modifier must take its own modifier off first.
 *
 * <p>Measured this session on 1506's talent: firing the grant once per point read {@code 20.436999998} for 100 points
 * instead of {@code 0.636999998} - exactly {@code 0.004 x (1+2+...+100)} - because every firing attached ANOTHER permanent
 * modifier and they all kept contributing.
 *
 * <p>⚠⚠ And the trap is the name of the flag one would reach for: {@code coexist: true} does NOT mean "replace my own
 * previous contribution". Its meaning in the engine is {@code setKeepsSiblings} ({@code TriggerInterpreter:3258}), i.e.
 * live beside OTHER sources' modifiers on the same attribute - which is why it must stay ON while the self-removal does the
 * recomputing.
 *
 * <p>The file has both halves: the behaviour, on a hand-made rule, and a sweep over ALL shipped content for the shape - so
 * a new rule of this kind cannot be added without either naming and removing its own modifier or being gated so that it can
 * only ever fire once.
 */
public class RecomputeNotAccumulateTest {
    /**
     * The events a rule can be fired on many times in one battle.
     */
    private static final Set<String> REPEATABLE = Set.of(
            "DEALING_DAMAGE", "TAKING_HIT", "HP_LOST", "HP_CONSUMED", "DAMAGE_SETTLED", "ATTACK_FINISHED", "SKILL_CAST",
            "BASIC_ATTACK", "ULT_CAST", "RESOURCE_CHANGED", "TURN_START", "TURN_END", "CRIT_DEALT", "BREAK",
            "ENERGY_GAINED", "HEALED", "ALLY_ATTACK", "FOLLOW_UP", "SKILL_POINT_SPENT", "SKILL_POINT_GAINED",
            "WAVE_START", "STATE_ENDED", "DEBUFF_APPLIED", "SUMMON_ATTACK", "WEAKNESS_ADDED", "EXTRA_TURN_STARTED");

    /**
     * Two firings: with a self-removal the attribute is stable, without one it compounds.
     */
    @Test
    public void aReFiringPermanentModifierMustRemoveItselfFirst() {
        double stable = firedTwice(true);
        double compounding = firedTwice(false);
        System.out.println("[recompute] base ATK 100, +50% of the base, attached twice: with a self-removal = "
                + stable + " ; without = " + compounding);
        Assertions.assertEquals(150.0, stable, 1e-9,
                "one 50% share of the 100 base, re-attached: 100 -> 150, and the second firing recomputes to the same 150");
        Assertions.assertTrue(compounding > stable + 1,
                "without the removal the second firing adds another share on top, so it compounds ("
                        + compounding + " vs " + stable + ") - this is the failure 1506's talent and her ramp both had");
    }

    /**
     * The sweep: every shipped rule of that shape either names + removes its own modifier, or is gated to fire once.
     */
    @Test
    public void everyShippedRuleOfThatShapeTakesItsOwnModifierOff() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<String> exempted = new ArrayList<>();
        for (Path file : contentFiles()) {
            var parsed = JsonParser.parseString(Files.readString(file));
            // ⚠ Not every file here is a character document: some are arrays (or fragments) and several are not rule
            // tables at all. Skipping anything that is not an object with a `rules` array keeps the sweep honest.
            if (!parsed.isJsonObject()) {
                continue;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("rules") || !root.get("rules").isJsonArray()) {
                continue;
            }
            for (var element : root.getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                String event = rule.has("on") ? rule.get("on").getAsString() : "";
                if (!REPEATABLE.contains(event) || !rule.has("do")) {
                    continue;
                }
                List<String> conditions = new ArrayList<>();
                if (rule.has("when")) {
                    rule.getAsJsonArray("when").forEach(c -> conditions.add(c.getAsString()));
                }
                // ⚠ A state gate is the other honest way to be safe: 1404's 「!self has_state 血仇」 means the rule can only
                // ever attach its modifier once, so it needs no removal. Named here so the exemption is a decision, not a hole.
                boolean gatedToOnce = conditions.stream().anyMatch(c -> c.contains("has_state"));
                for (var effectElement : rule.getAsJsonArray("do")) {
                    JsonObject effect = effectElement.getAsJsonObject();
                    if (!"MODIFY_ATTR".equals(text(effect, "op")) || !effect.has("permanent")
                            || !effect.get("permanent").getAsBoolean() || !effect.has("scale")) {
                        continue;
                    }
                    String name = text(effect, "buff");
                    boolean removed = name != null && rule.getAsJsonArray("do").asList().stream()
                            .map(item -> item.getAsJsonObject())
                            .anyMatch(other -> "REMOVE_STATE".equals(text(other, "op")) && name.equals(text(other, "buff")));
                    String where = file.getFileName() + " / " + text(rule, "id");
                    if (removed) {
                        continue;
                    }
                    if (gatedToOnce) {
                        exempted.add(where);
                    } else {
                        offenders.add(where + " (" + event + ", " + text(effect, "attribute") + ")");
                    }
                }
            }
        }
        System.out.println("[recompute] sweep: " + exempted.size() + " gated-to-once rule(s) allowed, offenders: "
                + offenders);
        Assertions.assertTrue(offenders.isEmpty(),
                "a rule that re-attaches a permanent scaled modifier on every " + REPEATABLE
                        + " firing must name it (`buff`) and remove it first, or be gated so it fires once: " + offenders);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double firedTwice(boolean withSelfRemoval) {
        Character unit = Character.fromAttributes("unit", 1000, 100, 100, 100);
        EffectSpec attach = new EffectSpec();
        TriggerSpecs.set(attach, "op", "MODIFY_ATTR");
        TriggerSpecs.set(attach, "attribute", "ATTACK");
        TriggerSpecs.set(attach, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(attach, "percent", 0.5);
        TriggerSpecs.set(attach, "permanent", true);
        TriggerSpecs.set(attach, "target", "self");
        List<EffectSpec> effects = new ArrayList<>();
        if (withSelfRemoval) {
            EffectSpec remove = new EffectSpec();
            TriggerSpecs.set(remove, "op", "REMOVE_STATE");
            TriggerSpecs.set(remove, "kind", "own");
            TriggerSpecs.set(remove, "target", "self");
            TriggerSpecs.set(attach, "buff", "test-attack");
            TriggerSpecs.set(remove, "buff", "test-attack");
            effects.add(remove);
        }
        effects.add(attach);
        unit.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), effects.toArray(new EffectSpec[0])))));

        Battle battle = new Battle(List.of(unit), List.of(), BattleFixture.seeded());
        for (int firing = 0; firing < 2; firing++) {
            battle.fireTriggers(TriggerEvent.TURN_START, unit, unit, 0, 0);
            battle.processRequests();
        }
        return unit.getAttribute(AttributeType.ATTACK).get();
    }

    /**
     * Every shipped character and light-cone file, skipping the registry of unwritten ones.
     */
    private static List<Path> contentFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String directory : List.of("src/main/resources/game/characters", "src/main/resources/game/light_cones")) {
            try (Stream<Path> stream = Files.list(Path.of(directory))) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().startsWith("_"))
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
