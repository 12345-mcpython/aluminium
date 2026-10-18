package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a whole-battle capture and reconstructs its <b>flow</b>: which casts happened, in what order, by whom, against
 * how many targets, and how much each of them dealt.
 *
 * <h2>How a cast is recovered from a flat event list</h2>
 * The capture is a flat list of damage events - no turn numbers, no cast ids. Two properties make the grouping
 * unambiguous in the captures seen so far:
 * <ol>
 *   <li><b>A hit is followed by its companion.</b> Every real hit is immediately followed by a {@code True} event with
 *       {@code attack_type 13} - a flat additional hit the kit adds per target. So one "hit group" = a non-13 event plus
 *       the 13 event right after it.</li>
 *   <li><b>One cast repeats the same skill value.</b> Consecutive hit groups share the attacker and the game's
 *       {@code raw_damage_before_modifiers}: an AoE over two enemies shows the same base twice, and a five-instance
 *       single-target attack shows it five times. A new base (or a new attacker) starts a new cast.</li>
 * </ol>
 * Under that rule a cast reports the number of <b>hit groups</b>, the number of <b>distinct targets</b>, and whether it
 * therefore looks like a single-target multi-hit or an area attack.
 *
 * <p>Usage: {@code capture-flow <capture.json>}.
 */
public final class CaptureFlow {

    /**
     * One cast of the reconstructed flow.
     *
     * @param index       1-based position in the flow
     * @param actor       the attacker's uid
     * @param kind        the hit's {@code type} ({@code Servant} / {@code Additional} / {@code Ultimate} / ...)
     * @param base        the game's {@code raw_damage_before_modifiers}
     * @param groups      hit groups (each with its true-damage companion)
     * @param targets     distinct {@code target_max_hp} values, i.e. distinct enemies
     * @param damage      the sum of the real hits
     * @param companion   the sum of the paired true-damage hits
     */
    private record Cast(int index, long actor, String kind, double base, int groups, int targets, double damage,
                        double companion, int covered) {
    }

    private CaptureFlow() {
    }

    /**
     * @param args {@code [capture.json]}
     * @return 0 when a flow was read, 1 when the capture had no events, 2 when it could not be read
     */
    public static int run(String... args) {
        Path path = null;
        for (String arg : args) {
            if (!"capture-flow".equals(arg) && !arg.startsWith("--")) {
                path = Path.of(arg);
            }
        }
        if (path == null) {
            System.out.println("usage: capture-flow <capture.json>");
            return 2;
        }
        JsonObject capture;
        try {
            capture = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            System.out.println("capture-flow: cannot read " + path + ": " + e);
            return 2;
        }
        JsonArray events = capture.getAsJsonArray("damage_events");
        if (events == null || events.isEmpty()) {
            System.out.println("capture-flow: " + path + " has no damage_events");
            return 1;
        }
        List<Cast> flow = group(events);
        System.out.println("capture: " + path.getFileName());
        System.out.printf(Locale.ROOT, "events:  %d ; casts: %d%n", events.size(), flow.size());
        System.out.printf(Locale.ROOT, "%-4s %-6s %-11s %12s %6s %7s %14s %12s%n",
                "cast", "actor", "kind", "base", "hits", "targets", "damage", "companion");
        System.out.println("-".repeat(78));
        double totalReal = 0;
        double totalCompanion = 0;
        int covered = 0;
        for (Cast cast : flow) {
            totalReal += cast.damage();
            covered += cast.covered();
            totalCompanion += cast.companion();
            System.out.printf(Locale.ROOT, "%-4d %-6d %-11s %12.2f %6d %7d %14.2f %12.2f%n",
                    cast.index(), cast.actor(), cast.kind(), cast.base(), cast.groups(), cast.targets(),
                    cast.damage(), cast.companion());
        }
        System.out.println("-".repeat(78));
        System.out.printf(Locale.ROOT, "total: %.2f real + %.2f companion = %.2f%n",
                totalReal, totalCompanion, totalReal + totalCompanion);
        System.out.printf(Locale.ROOT, "covered: %d / %d events%n", covered, events.size());
        System.out.println();
        System.out.println("per actor (in the order they first acted):");
        Map<Long, int[]> byActor = new LinkedHashMap<>();
        for (Cast cast : flow) {
            byActor.computeIfAbsent(cast.actor(), key -> new int[2]);
            byActor.get(cast.actor())[0]++;
        }
        TreeMap<Integer, Long> firstSeen = new TreeMap<>();
        for (Cast cast : flow) {
            firstSeen.putIfAbsent(cast.index(), cast.actor());
        }
        for (Map.Entry<Long, int[]> entry : byActor.entrySet()) {
            System.out.printf(Locale.ROOT, "  %-6d casts: %d%n", entry.getKey(), entry.getValue()[0]);
        }
        return 0;
    }

    /**
     * Groups the flat event list into casts (see the class comment for the rule).
     */
    static List<Cast> group(JsonArray events) {
        List<Cast> casts = new ArrayList<>();
        int i = 0;
        while (i < events.size()) {
            JsonObject head = events.get(i).getAsJsonObject();
            long actor = head.getAsJsonObject("attacker").get("uid").getAsLong();
            JsonObject headZones = head.getAsJsonObject("zones");
            double base = numberOr(headZones, "raw_damage_before_modifiers", 0);
            String kind = text(head, "type");
            // Skip a stray companion with no hit before it (cannot happen in the captures seen, but the grouping must
            // not depend on that).
            if (isCompanion(headZones)) {
                i++;
                continue;
            }
            int index = casts.size() + 1;
            int groups = 0;
            int covered = 0;
            double damage = 0;
            double companion = 0;
            java.util.Set<Double> targets = new java.util.LinkedHashSet<>();
            while (i < events.size()) {
                JsonObject event = events.get(i).getAsJsonObject();
                if (event.getAsJsonObject("attacker").get("uid").getAsLong() != actor) {
                    break;
                }
                JsonObject zones = event.getAsJsonObject("zones");
                // The companion comes FIRST: it belongs to the hit right before it no matter what its own base is (its
                // base is a flat value of its own - measured 371.47 against the hit's 177.60), so testing the base before
                // recognising it split every hit from its companion.
                if (isCompanion(zones)) {
                    companion += numberOr(event, "damage", 0);
                    covered++;
                    i++;
                    continue;
                }
                if (Math.abs(numberOr(zones, "raw_damage_before_modifiers", 0) - base) > 1e-6) {
                    break;
                }
                groups++;
                covered++;
                damage += numberOr(event, "damage", 0);
                targets.add(numberOr(zones, "target_max_hp", 0));
                i++;
            }
            casts.add(new Cast(index, actor, kind, base, groups, targets.size(), damage, companion, covered));
        }
        return casts;
    }

    private static boolean isCompanion(JsonObject zones) {
        return Math.abs(numberOr(zones, "attack_type", 0) - 13) < 1e-9;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static double numberOr(JsonObject object, String key, double fallback) {
        JsonElement value = object == null ? null : object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
    }
}
