package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.enums.DamageElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replays a whole-battle capture (the `veritas` tool's JSON) through the engine, one damage event at a time.
 *
 * <h2>What this is for</h2>
 * A capture states, per hit, every multiplier the game applied - the base, the boost sum, crit, the DEF the game
 * actually used, the net resistance, the reduction, the vulnerability. Comparing the engine's {@code breakdown()} on
 * those inputs is the strongest statement available short of driving a live battle: it exercises the engine's own
 * zone arithmetic on numbers nobody derived from the engine.
 *
 * <h2>How an event becomes a scenario</h2>
 * <ul>
 *   <li>The element comes from the payload's {@code damage_type} bit mask (the game's own {@code DamageType}); that
 *       decides which {@code *_DAMAGE_BOOST} the engine reads and which of the target's resistances it uses.</li>
 *   <li>The base is the payload's {@code raw_damage_before_modifiers}.</li>
 *   <li>{@code DAMAGE_PENETRATION} is solved so that the engine's {@code 1 - (res - pen)} equals the payload's
 *       {@code 1 - net_resistance}, whatever the fixture monster resists.</li>
 *   <li>DEF-ignore is {@code 1 - target_defence_effective / target_defence} - the capture's own two numbers, so the
 *       engine is asked to reproduce the game's effective defence from the reduction rather than from the result.</li>
 *   <li>The payload folds the toughness reduction into {@code all_damage_reduce}; the scenario marks the target broken
 *       and states that number as a reduction modifier, which makes the engine's own product equal to the game's.</li>
 * </ul>
 *
 * <p>Usage: {@code compare-capture <capture.json> [--limit N]}.
 */
public final class CaptureComparator {

    private static final String[] ELEMENTS = {"physical", "fire", "ice", "thunder", "wind", "quantum", "imaginary"};
    private static final int[] ELEMENT_BITS = {1, 2, 4, 8, 16, 32, 64};

    /**
     * A stand-in per element: the capture states every modifier of the hit, so the character only has to supply a
     * cast of the right element. These are the ones whose COMMON has no split in {@code skill_effects.json}.
     */
    private static final Map<String, Integer> STAND_IN = Map.of(
            "physical", 1105, "fire", 1003, "ice", 1001, "thunder", 1005,
            "wind", 1101, "quantum", 1006, "imaginary", 1004);

    private CaptureComparator() {
    }

    /**
     * @param args {@code [path]} (+ optional {@code --limit N})
     * @return 0 when every event reproduced, 1 when any differed, 2 when the capture could not be read
     */
    public static int run(String... args) {
        if (args == null || args.length < 1) {
            System.out.println("usage: compare-capture <capture.json> [--limit N]");
            return 2;
        }
        // `Main` hands over the whole command line, mode included: skip the mode word itself.
        Path path = null;
        int limit = Integer.MAX_VALUE;
        for (int i = 0; i < args.length; i++) {
            if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if (path == null && !"compare-capture".equals(args[i])) {
                path = Path.of(args[i]);
            }
        }
        if (path == null) {
            System.out.println("usage: compare-capture <capture.json> [--limit N]");
            return 2;
        }
        JsonObject capture;
        try {
            capture = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            System.out.println("compare-capture: cannot read " + path + ": " + e);
            return 2;
        }
        JsonArray events = capture.getAsJsonArray("damage_events");
        if (events == null) {
            System.out.println("compare-capture: " + path + " has no damage_events");
            return 2;
        }
        System.out.println("capture:  " + path.getFileName());
        System.out.printf(Locale.ROOT, "events:   %d (replaying up to %s)%n",
                events.size(), limit == Integer.MAX_VALUE ? "all" : String.valueOf(limit));
        System.out.printf(Locale.ROOT, "%-4s %-11s %-9s %16s %16s %10s%n",
                "#", "type", "element", "game", "engine", "relative");
        System.out.println("-".repeat(74));

        int ok = 0;
        int off = 0;
        int skipped = 0;
        for (int i = 0; i < events.size() && i < limit; i++) {
            JsonObject event = events.get(i).getAsJsonObject();
            String type = text(event, "type");
            double damage = number(event, "damage");
            JsonObject zones = event.getAsJsonObject("zones");
            if (zones == null) {
                skipped++;
                continue;
            }
            if ("true".equalsIgnoreCase(type)) {
                // True damage bypasses the chain: the game's own value IS the settled number.
                double value = number(zones, "damage_value");
                boolean same = Math.abs(damage - value) <= Math.max(1e-6, Math.abs(damage) * 1e-9);
                System.out.printf(Locale.ROOT, "%-4d %-11s %-9s %16.6f %16.6f %9.2e%% %s%n",
                        i, type, "-", damage, value, same ? 0.0 : (value - damage) / damage * 100,
                        same ? "" : "  <-- differs");
                if (same) {
                    ok++;
                } else {
                    off++;
                }
                continue;
            }
            String element = elementOf(zones);
            if (element == null) {
                skipped++;
                continue;
            }
            DamageScenario scenario = scenarioOf(i, type, damage, zones, element);
            if (scenario == null) {
                System.out.printf(Locale.ROOT, "%-4d %-11s %-9s %16.6f %16s %10s  (skipped: %s)%n", i, type, element,
                        damage, "-", "-", skipReason(zones));
                skipped++;
                continue;
            }
            Map<String, Double> engine;
            try {
                DamageComparator.Result result = DamageComparator.measure(scenario);
                engine = result.instances().get(result.reported()).breakdown();
            } catch (RuntimeException e) {
                System.out.printf(Locale.ROOT, "%-4d %-11s %-9s  the engine could not run this event: %s%n", i, type,
                        element, e);
                off++;
                continue;
            }
            double predicted = engine.getOrDefault("final", Double.NaN);
            double relative = (predicted - damage) / damage;
            boolean same = Math.abs(relative) <= 1e-6 || Math.abs(predicted - damage) <= 1e-6;
            double expectedBase = scenario.getExpect().getOrDefault("base", Double.NaN);
            double engineBase = engine.getOrDefault("base", Double.NaN);
            boolean baseOk = Math.abs(engineBase - expectedBase) <= Math.max(1e-6, Math.abs(expectedBase) * 1e-9);
            System.out.printf(Locale.ROOT, "%-4d %-11s %-9s %16.6f %16.6f %9.2e%% %s%n",
                    i, type, element, damage, predicted, relative * 100,
                    (same && baseOk) ? "" : (same ? "  <-- base differs" : "  <-- differs"));
            if (same && baseOk) {
                ok++;
            } else {
                off++;
            }
        }
        System.out.println("-".repeat(74));
        System.out.printf(Locale.ROOT, "reproduced: %d / %d (skipped %d)%n", ok, ok + off, skipped);
        System.out.println(off == 0
                ? "RESULT: every event reproduces"
                : "RESULT: " + off + " event(s) differ");
        return off == 0 ? 0 : 1;
    }

    /**
     * Builds the scenario that asks the engine for one capture event.
     */
    private static DamageScenario scenarioOf(int index, String type, double damage, JsonObject zones, String element) {
        double base = baseOf(zones, element);
        if (Double.isNaN(base) || base <= 0) {
            return null;
        }
        DamageScenario scenario = new DamageScenario();
        scenario.setName("capture event #" + index + " (" + type + ", " + element + ")");
        scenario.setCrit(flag(zones, "is_critical"));

        DamageScenario.Attacker attacker = new DamageScenario.Attacker();
        attacker.setCid(STAND_IN.get(element));
        attacker.setLevel((int) numberOr(zones, "level", 80));
        attacker.setEidolon(0);
        attacker.setSkill("COMMON");
        attacker.setSkillLevel(1);
        attacker.setBase(base);
        attacker.setSuppressContent(true);
        attacker.setAttributes(attackerAttributes(zones, element));
        scenario.setAttacker(attacker);

        DamageScenario.Target target = new DamageScenario.Target();
        target.setMonsterId(monsterWithResistance(zones, element));
        target.setLevel((int) numberOr(zones, "target_level", 90));
        target.setBroken(true);
        Map<String, Double> targetAttributes = new LinkedHashMap<>();
        targetAttributes.put("DEFENCE", zones.has("target_defence") ? number(zones, "target_defence") : number(zones, "target_defence_effective"));
        targetAttributes.put("HEALTH", Math.max(1.0e9, numberOr(zones, "target_max_hp", 1.0e9) * 4));
        target.setAttributes(targetAttributes);
        List<DamageScenario.Target.Modifier> buffs = new ArrayList<>();
        double vulnerable = numberOr(zones, "all_damage_type_taken_ratio", 0)
                + takenRatio(zones, element);
        if (vulnerable != 0) {
            buffs.add(modifier("vulnerable", vulnerable));
        }
        double reduction = numberOr(zones, "all_damage_reduce", 0);
        if (reduction != 0) {
            buffs.add(modifier("reduction", reduction));
        }
        target.setBuffs(buffs);
        scenario.setTarget(target);

        scenario.setInstance(0);
        scenario.setInstances(1);
        scenario.setTolerance(1e-6);
        Map<String, Double> expect = new LinkedHashMap<>();
        expect.put("base", base);
        expect.put("final", damage);
        scenario.setExpect(expect);
        return scenario;
    }

    /**
     * Why an event cannot go through this driver.
     *
     * <p>Break damage is the one: its chain does NOT take the element / all-type boost bucket (verified on two
     * captures: folding the boost in turns the residual from 1.147 into 0.387, which is impossible), while the cast
     * this driver installs is a normal hit that does. The break path is verified separately - see the audit in
     * {@code scenarios/README.md}: {@code 3767.553 x break_damage_percentage x (1 + break_damage_added_ratio)}.
     */
    private static String skipReason(JsonObject zones) {
        int formula = (int) numberOr(zones, "attack_formula_type", 0);
        if (formula == 7) {
            return "break damage: its chain skips the boost bucket this driver's cast applies";
        }
        return "no base or element";
    }

    private static Map<String, Double> attackerAttributes(JsonObject zones, String element) {
        Map<String, Double> attributes = new LinkedHashMap<>();
        // The seven element boosts are stated explicitly: a stand-in character's own traces add some (measured: 1002
        // carries 0.224 of wind boost, 1003 carries 0.374 of fire boost), and a capture replay must not inherit them.
        for (String each : ELEMENTS) {
            attributes.put(each.toUpperCase(Locale.ROOT) + "_DAMAGE_BOOST", 0.0);
        }
        attributes.put(element.toUpperCase(Locale.ROOT) + "_DAMAGE_BOOST", addedRatio(zones, element));
        attributes.put("ALL_DAMAGE_TYPE_BOOST", numberOr(zones, "all_damage_type_added_ratio", 0)
                + numberOr(zones, "all_damage_added_ratio", 0));
        attributes.put("DOT_DAMAGE_BOOST", numberOr(zones, "dot_damage_added_ratio", 0));
        attributes.put("COMBO_DAMAGE_BOOST", numberOr(zones, "combo_damage_added_ratio", 0));
        attributes.put("FINAL_DAMAGE_BOOST", 0.0);
        attributes.put("BASIC_ATTACK_DAMAGE_BOOST", 0.0);
        attributes.put("SKILL_DAMAGE_BOOST", 0.0);
        attributes.put("BASE_DAMAGE_MULTI_RATIO", numberOr(zones, "base_damage_multi_ratio", 1.0) - 1.0);
        attributes.put("CRIT_CHANCE", 1.0);
        attributes.put("CRIT_ATTACK", numberOr(zones, "critical_damage", 0));
        attributes.put("DEFENCE_IGNORE", defenceIgnore(zones));
        attributes.put("DAMAGE_PENETRATION", Math.max(0.0, resistance(zones, element) - numberOr(zones, "net_resistance", 0)));
        return attributes;
    }

    /** {@code 1 - effective / base}, clamped: the two DEF numbers the capture states. */
    private static double defenceIgnore(JsonObject zones) {
        if (!zones.has("target_defence_effective") || !zones.has("target_defence")) {
            return 0.0;
        }
        double effective = number(zones, "target_defence_effective");
        double base = number(zones, "target_defence");
        if (base <= 0) {
            return 0.0;
        }
        return Math.clamp(1.0 - effective / base, 0.0, 1.0);
    }

    /** The payload's base layer: {@code raw_damage_before_modifiers}, or the stat row when it is absent. */
    private static double baseOf(JsonObject zones, String element) {
        int formula = (int) numberOr(zones, "attack_formula_type", 1);
        if (formula == 7) {
            // Break damage takes its own bucket (see skipReason); a normal cast would apply the wrong one.
            return Double.NaN;
        }
        if (zones.has("raw_damage_before_modifiers")) {
            return number(zones, "raw_damage_before_modifiers");
        }
        String stat = switch (formula) {
            case 1 -> "attacker_attack_lp";
            case 2 -> "attacker_defence";
            case 3 -> "attacker_hp_lp";
            case 4 -> "attacker_current_hp";
            case 5 -> "target_max_hp";
            case 6 -> "target_current_hp";
            default -> null;
        };
        if (stat == null || !zones.has(stat)) {
            return Double.NaN;
        }
        return number(zones, stat) * numberOr(zones, "damage_percentage", 0) + numberOr(zones, "damage_value", 0);
    }

    /** The element, from the game's own {@code damage_type} bit mask. */
    private static String elementOf(JsonObject zones) {
        if (zones.has("damage_type")) {
            int mask = (int) number(zones, "damage_type");
            for (int i = 0; i < ELEMENT_BITS.length; i++) {
                if (mask == ELEMENT_BITS[i]) {
                    return ELEMENTS[i];
                }
            }
        }
        for (String each : ELEMENTS) {
            if (zones.has("added_ratio") && zones.getAsJsonObject("added_ratio").has(each)
                    && number(zones.getAsJsonObject("added_ratio"), each) != 0) {
                return each;
            }
        }
        return null;
    }

    private static double addedRatio(JsonObject zones, String element) {
        return zones.has("added_ratio") && zones.getAsJsonObject("added_ratio").has(element)
                ? number(zones.getAsJsonObject("added_ratio"), element) : 0.0;
    }

    private static double takenRatio(JsonObject zones, String element) {
        return zones.has("taken_ratio") && zones.getAsJsonObject("taken_ratio").has(element)
                ? number(zones.getAsJsonObject("taken_ratio"), element) : 0.0;
    }

    private static double resistance(JsonObject zones, String element) {
        return zones.has("resistance") && zones.getAsJsonObject("resistance").has(element)
                ? number(zones.getAsJsonObject("resistance"), element) : 0.0;
    }

    /**
     * A monster whose resistance for this element equals the capture's, so the solver above reduces to the game's own
     * penetration; falls back to the first monster when none matches.
     */
    private static int monsterWithResistance(JsonObject zones, String element) {
        double wanted = resistance(zones, element);
        DamageElement damageElement = DamageElement.valueOf(element.toUpperCase(Locale.ROOT));
        int fallback = 1002011;
        for (Map.Entry<Integer, MonsterConfig> entry : Constant.MONSTER_CONFIGS.entrySet()) {
            Map<DamageElement, Double> resistances = entry.getValue().damageResistance();
            double mine = resistances == null ? 0.0 : resistances.getOrDefault(damageElement, 0.0);
            if (Math.abs(mine - wanted) <= 1e-6) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    private static DamageScenario.Target.Modifier modifier(String kind, double value) {
        DamageScenario.Target.Modifier modifier = new DamageScenario.Target.Modifier();
        modifier.setKind(kind);
        modifier.setValue(value);
        modifier.setTurns(1);
        return modifier;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static double number(JsonObject object, String key) {
        return object.get(key).getAsDouble();
    }

    private static double numberOr(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }
}
