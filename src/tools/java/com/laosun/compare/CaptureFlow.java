package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a whole-battle capture and reconstructs its <b>flow</b>: which casts happened, in what order, by whom,
 * against how many targets, and how much each of them dealt.
 *
 * <h2>How a cast is recovered from a flat event list</h2>
 * The capture is a flat list of damage events - no turn numbers, no cast ids. Three properties of the payload make the
 * grouping unambiguous in the captures seen so far:
 * <ol>
 *   <li><b>The attack type says what kind of event it is.</b> {@code zones.attack_type} is the game's own classifier:
 *       {@code 1} basic attack, {@code 3} ultimate, {@code 12} a memosprite's skill, {@code 13} the {@code True}
 *       companion a kit adds per hit, {@code 6} additional damage a talent/ultimate produced, {@code 10} a weakness
 *       break. A <b>new cast</b> starts when either the attacker or the attack type changes.</li>
 *   <li><b>A cast may repeat different bases.</b> The memosprite 长夜's skill 1 is ONE cast whose six instances carry
 *       {@code HitSplitRatio} 0.1 x 5 + 0.5 (see {@code skill_effects.json}), so its payload shows five smaller bases
 *       and one five times larger. Grouping by base (the previous rule) split that single cast into "5 + 1" and made
 *       the flow look like five segments - measured wrong against the ability's six damage tasks.</li>
 *   <li><b>Companions follow their hits, in order.</b> Every real hit is paired with a {@code True} companion
 *       ({@code attack_type 13}) of its own, and the companions arrive in the same order as the hits: the payload for
 *       a break followed by a basic attack shows {@code break, basic, break-companion, basic-companion}. They are
 *       therefore attached first-in-first-out, to the oldest hit still waiting for one.</li>
 * </ol>
 * Under that rule each group reports the number of <b>hits</b>, the number of <b>distinct targets</b>, and whether it
 * looks like a single-target multi-hit or an area attack. Groups are also classified as {@code cast}, {@code derived}
 * (additional damage a kit produced - not a cast of its own) or {@code break}.
 *
 * <p>Usage: {@code capture-flow <capture.json>}.
 */
public final class CaptureFlow {

    /**
     * One group of the reconstructed flow.
     *
     * @param index          1-based position in the flow
     * @param actor          the attacker's uid
     * @param kind           the hit's {@code type} ({@code Servant} / {@code Additional} / {@code Ultimate} / ...)
     * @param classification {@code cast} / {@code derived} / {@code break}
     * @param base           the first hit's {@code raw_damage_before_modifiers}
     * @param hits           damage instances ({@code True} companions excluded)
     * @param targets        distinct {@code target_max_hp} values, i.e. distinct enemies
     * @param damage         the sum of the real hits
     * @param companion      the sum of the paired true-damage hits
     * @param covered        how many events this group accounts for
     * @param firstTarget    the {@code target_max_hp} of the group's FIRST hit - which unit the cast was aimed at,
     *                       as the payload's only identity for a target. {@code BattleReplay} uses it to check that
     *                       its plan names the same target the capture does.
     * @param hitBases       every hit's {@code raw_damage_before_modifiers}, in the order the payload lists them -
     *                       {@code BattleReplay} compares these against the engine's own settled bases, one for one
     * @param percentage     the first hit's {@code damage_percentage} - {@code 0} means the base did not come from a
     *                       multiplier row at all (1409's tally-delivered hits), so there is no share to compare
     * @param attack         the first hit's {@code attacker_attack_lp}, i.e. the attacker's panel as the game saw it
     * @param health         the first hit's {@code attacker_hp_lp}
     * @param defence        the first hit's {@code attacker_defence}
     */
    record Cast(int index, long actor, String kind, String classification, double base, int hits, int targets,
                double damage, double companion, int covered, double firstTarget, List<Double> hitBases,
                double percentage, double attack, double health, double defence, long skillId) {
    }

    /**
     * A group being accumulated; a record cannot grow, and the companions of a hit may arrive after the next cast
     * has already started (see the class comment), so the groups are mutable until the event list ends.
     */
    private static final class Group {
        private final int index;
        private final long actor;
        private final String kind;
        private final String classification;
        private final int attackType;
        private final LinkedHashSet<Double> targets = new LinkedHashSet<>();
        private final List<Double> hitBases = new ArrayList<>();
        private double base;
        private double percentage;
        private double attack;
        private double health;
        private double defence;
        private int hits;
        private double damage;
        private double companion;
        private int covered;
        private double firstTarget;
        private long skillId;

        private Group(int index, long actor, String kind, String classification, int attackType) {
            this.index = index;
            this.actor = actor;
            this.kind = kind;
            this.classification = classification;
            this.attackType = attackType;
        }

        private Cast toCast() {
            return new Cast(index, actor, kind, classification, base, hits, targets.size(), damage, companion, covered,
                    firstTarget, List.copyOf(hitBases), percentage, attack, health, defence, skillId);
        }
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
        int casts = 0;
        int derived = 0;
        int breaks = 0;
        for (Cast cast : flow) {
            switch (cast.classification()) {
                case "cast" -> casts++;
                case "derived" -> derived++;
                case "break" -> breaks++;
                default -> {
                }
            }
        }
        System.out.printf(Locale.ROOT, "events:  %d ; casts: %d ; derived hits: %d ; breaks: %d%n",
                events.size(), casts, derived, breaks);
        System.out.printf(Locale.ROOT, "%-4s %-6s %-11s %12s %6s %7s %14s %12s %-8s%n",
                "cast", "actor", "kind", "base", "hits", "targets", "damage", "companion", "class");
        System.out.println("-".repeat(88));
        double totalReal = 0;
        double totalCompanion = 0;
        int covered = 0;
        for (Cast cast : flow) {
            totalReal += cast.damage();
            covered += cast.covered();
            totalCompanion += cast.companion();
            System.out.printf(Locale.ROOT, "%-4d %-6d %-11s %12.2f %6d %7d %14.2f %12.2f %-8s%n",
                    cast.index(), cast.actor(), cast.kind(), cast.base(), cast.hits(), cast.targets(),
                    cast.damage(), cast.companion(), cast.classification());
        }
        System.out.println("-".repeat(88));
        System.out.printf(Locale.ROOT, "total: %.2f real + %.2f companion = %.2f%n",
                totalReal, totalCompanion, totalReal + totalCompanion);
        System.out.printf(Locale.ROOT, "covered: %d / %d events%n", covered, events.size());
        System.out.println();
        System.out.println("per actor (in the order they first acted):");
        Map<Long, int[]> byActor = new LinkedHashMap<>();
        for (Cast cast : flow) {
            if (!"cast".equals(cast.classification())) {
                continue;                               // derived hits and breaks are not casts
            }
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
        if (!events.isEmpty() && events.get(0).getAsJsonObject().has("cast_index")) {
            return groupByCastIndex(events);
        }
        List<Group> groups = new ArrayList<>();
        // Hits still waiting for their True companion, oldest first: companions arrive in the hits' own order, and a
        // hit's companion may arrive after the next cast has already started (measured: break, basic, break-companion,
        // basic-companion).
        ArrayDeque<Group> pending = new ArrayDeque<>();
        Group current = null;
        for (int i = 0; i < events.size(); i++) {
            JsonObject event = events.get(i).getAsJsonObject();
            JsonObject zones = event.getAsJsonObject("zones");
            long actor = event.getAsJsonObject("attacker").get("uid").getAsLong();
            int attackType = (int) Math.rint(numberOr(zones, "attack_type", 0));
            if (attackType == 13) {
                Group waiting = pending.poll();
                if (waiting != null) {
                    waiting.companion += numberOr(event, "damage", 0);
                    waiting.covered++;
                }
                continue;                               // a stray companion is simply not paired
            }
            if (current == null || current.actor != actor || current.attackType != attackType) {
                current = new Group(groups.size() + 1, actor, text(event, "type"), classify(attackType), attackType);
                groups.add(current);
            }
            double base = numberOr(zones, "raw_damage_before_modifiers", 0);
            if (current.hits == 0) {
                current.base = base;
                current.firstTarget = numberOr(zones, "target_max_hp", 0);
                // The panel the game itself used for this hit: BattleReplay installs it before driving the cast, so a
                // window whose panel moved mid-fight is replayed at the panel the capture's own arithmetic used.
                current.percentage = numberOr(zones, "damage_percentage", 0);
                current.attack = numberOr(zones, "attacker_attack_lp", 0);
                current.health = numberOr(zones, "attacker_hp_lp", 0);
                current.defence = numberOr(zones, "attacker_defence", 0);
            }
            current.hits++;
            current.hitBases.add(base);
            current.damage += numberOr(event, "damage", 0);
            current.targets.add(numberOr(zones, "target_max_hp", 0));
            current.covered++;
            pending.add(current);
        }
        List<Cast> casts = new ArrayList<>(groups.size());
        for (Group group : groups) {
            casts.add(group.toCast());
        }
        return casts;
    }

    /**
     * Groups by the payload's OWN cast identity - {@code cast_index} - instead of inferring it from adjacency.
     *
     * <p>Why it exists: the older capture had no cast identity, so a cast was "the events whose actor and attack type
     * did not change", and a rider delivered after the next cast had started was folded into the wrong one. The newer
     * payload states the index, so the grouping is a partition of the payload rather than a guess. Within one index the
     * old split still applies (a cast's own hits are one group, its rule-delivered hits and its break another), because
     * {@code BattleReplay} compares a cast row against the skill's own instances only.
     *
     * @param events the payload's damage events, in payload order
     * @return one {@link Cast} per (cast_index, actor, attack type) run, carrying the payload's index
     */
    static List<Cast> groupByCastIndex(JsonArray events) {
        List<Group> groups = new ArrayList<>();
        ArrayDeque<Group> pending = new ArrayDeque<>();
        Group current = null;
        int currentCast = Integer.MIN_VALUE;
        for (int i = 0; i < events.size(); i++) {
            JsonObject event = events.get(i).getAsJsonObject();
            JsonObject zones = event.getAsJsonObject("zones");
            long actor = event.getAsJsonObject("attacker").get("uid").getAsLong();
            int attackType = (int) Math.rint(numberOr(zones, "attack_type", 0));
            int castIndex = (int) Math.rint(numberOr(event, "cast_index", 0));
            if (attackType == 13) {
                Group waiting = pending.poll();
                if (waiting != null) {
                    waiting.companion += numberOr(event, "damage", 0);
                    waiting.covered++;
                }
                continue;
            }
            if (current == null || currentCast != castIndex || current.actor != actor
                    || current.attackType != attackType) {
                current = new Group(castIndex, actor, text(event, "type"), classify(attackType), attackType);
                current.skillId = (long) numberOr(event, "skill_id", 0);
                groups.add(current);
                currentCast = castIndex;
            }
            double base = numberOr(zones, "raw_damage_before_modifiers", 0);
            if (current.hits == 0) {
                current.base = base;
                current.firstTarget = numberOr(zones, "target_max_hp", 0);
                current.percentage = numberOr(zones, "damage_percentage", 0);
                current.attack = numberOr(zones, "attacker_attack_lp", 0);
                current.health = numberOr(zones, "attacker_hp_lp", 0);
                current.defence = numberOr(zones, "attacker_defence", 0);
            }
            current.hits++;
            current.hitBases.add(base);
            current.damage += numberOr(event, "damage", 0);
            current.targets.add(numberOr(zones, "target_max_hp", 0));
            current.covered++;
            pending.add(current);
        }
        List<Cast> casts = new ArrayList<>(groups.size());
        for (Group group : groups) {
            casts.add(group.toCast());
        }
        return casts;
    }

    /**
     * What kind of event an attack type is: a cast of its own, additional damage a cast produced, or a weakness break.
     */
    private static String classify(int attackType) {
        return switch (attackType) {
            case 6 -> "derived";
            case 10 -> "break";
            default -> "cast";
        };
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
