package com.laosun.compare;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Audits the rule block a capture attaches to rule-delivered damage ({@code OnDamage.rule}).
 *
 * <p><b>Why this exists.</b> Those instances carry no {@code damage_percentage} - the game stores only the evaluated
 * {@code DamageValue} - so a replay compared them against nothing and called them {@code n/a}. The richer payload states how
 * each one was built, and the shapes differ:
 *
 * <ul>
 *   <li>{@code share_of: "HEAL_TALLY"} - {@code base = 累计治疗 x params[0]}; the counter is {@code base / params[0]};</li>
 *   <li>{@code share_of: "ATTACK"} - {@code base = attacker_attack x damage_ratio}, where the ratio is the talent/buff
 *       multiplier (measured: 1.068333 / 0.933343 / 2.708076 / 0.847983 / 0.855299 on the five such rows of the 2026-10-30
 *       fight) - so {@code damage_ratio} is recoverable from the payload's own two numbers;</li>
 *   <li>{@code share_of: "RULE"} - neither; the expression's hashes name the inputs instead.</li>
 * </ul>
 *
 * <p>This reads those blocks and checks what can be checked <b>inside</b> the payload: for an {@code ATTACK} row, that
 * {@code base / attacker_attack} really is the stated ratio; for a {@code HEAL_TALLY} row, that {@code params[0]} is
 * non-zero and the implied counter is finite. A payload that states neither (every capture before this one) is reported as
 * "no rule block", not as a failure.
 */
public final class RuleAudit {

    private RuleAudit() {
    }

    /**
     * What one capture's rule blocks say.
     *
     * @param byShape       {@code share_of} to how many events carry it
     * @param checked       how many rows had enough numbers to be checked
     * @param problems      rows whose own numbers disagree, one line each
     * @param impliedCounters {@code HEAL_TALLY} rows: the counter each row implies, by cast index
     */
    public record Report(Map<String, Integer> byShape, int checked, List<String> problems,
                         Map<Integer, Double> impliedCounters) {
    }

    /**
     * Audits every event that carries a {@code rule} block.
     *
     * @param capture the parsed capture
     * @return the report
     */
    public static Report of(JsonObject capture) {
        Map<String, Integer> byShape = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Map<Integer, Double> counters = new LinkedHashMap<>();
        int checked = 0;
        JsonArray events = capture.getAsJsonArray("damage_events");
        if (events == null) {
            return new Report(byShape, 0, problems, counters);
        }
        for (JsonElement element : events) {
            JsonObject event = element.getAsJsonObject();
            if (!event.has("rule") || event.get("rule").isJsonNull()) {
                continue;
            }
            JsonObject rule = event.getAsJsonObject("rule");
            String shape = rule.has("share_of") && !rule.get("share_of").isJsonNull()
                    ? rule.get("share_of").getAsString() : "(unnamed)";
            byShape.merge(shape, 1, Integer::sum);
            int cast = event.has("cast_index") ? event.get("cast_index").getAsInt() : 0;
            JsonObject zones = event.getAsJsonObject("zones");
            double base = zones.has("damage_value") ? zones.get("damage_value").getAsDouble()
                    : zones.get("raw_damage_before_modifiers").getAsDouble();

            if ("ATTACK".equals(shape)) {
                checked++;
                double ratio = rule.has("damage_ratio") && !rule.get("damage_ratio").isJsonNull()
                        ? rule.get("damage_ratio").getAsDouble() : Double.NaN;
                double attack = zones.has("attacker_attack") ? zones.get("attacker_attack").getAsDouble() : Double.NaN;
                if (Double.isNaN(ratio) || Double.isNaN(attack) || attack == 0) {
                    problems.add("cast " + cast + ": an ATTACK row states no damage_ratio or no attacker_attack");
                } else {
                    double measured = base / attack;
                    if (Math.abs(measured - ratio) > Math.max(1e-6, Math.abs(ratio) * 1e-6)) {
                        problems.add(String.format(Locale.ROOT,
                                "cast %d: base / attacker_attack = %.6f but the rule states %.6f", cast, measured, ratio));
                    }
                }
            } else if ("HEAL_TALLY".equals(shape)) {
                checked++;
                JsonArray params = rule.getAsJsonArray("params");
                double rate = params == null || params.isEmpty() ? Double.NaN : params.get(0).getAsDouble();
                if (Double.isNaN(rate) || rate <= 0) {
                    problems.add("cast " + cast + ": a HEAL_TALLY row states no usable params[0]");
                } else {
                    counters.put(cast, base / rate);
                }
            }
        }
        return new Report(byShape, checked, problems, counters);
    }

    /**
     * {@code rule-audit <capture.json>} - prints what the payload's rule blocks say and whether their numbers agree.
     *
     * @param args the capture path
     * @return 0 when every checkable row agreed, 1 when one did not, 2 when the capture could not be read
     */
    public static int run(String... args) {
        Path path = null;
        for (String arg : args) {
            if (!"rule-audit".equals(arg) && !arg.startsWith("--")) {
                path = Path.of(arg);
            }
        }
        if (path == null) {
            System.out.println("usage: rule-audit <capture.json>");
            return 2;
        }
        JsonObject capture;
        try {
            capture = com.google.gson.JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            System.out.println("rule-audit: cannot read " + path + " (" + e + ")");
            return 2;
        }
        Report report = of(capture);
        if (report.byShape().isEmpty()) {
            System.out.println("rule-audit: this capture states no rule block on any event");
            return 0;
        }
        System.out.println("rule blocks by share_of: " + report.byShape());
        System.out.printf(Locale.ROOT, "rows with enough numbers to check: %d ; disagreements: %d%n",
                report.checked(), report.problems().size());
        for (String problem : report.problems()) {
            System.out.println("  " + problem);
        }
        if (!report.impliedCounters().isEmpty()) {
            System.out.println("counters a HEAL_TALLY row implies (cast -> value): " + report.impliedCounters());
        }
        Map<String, Object> stage = onStageCheck(capture);
        System.out.println("field.on_stage vs what each cast actually hit: " + stage);
        Map<String, java.util.TreeSet<Double>> rates = parentRates(capture);
        if (!rates.isEmpty()) {
            System.out.println("each derived instance's rate against its parent (type|rule -> distinct base/parent):");
            for (Map.Entry<String, java.util.TreeSet<Double>> each : rates.entrySet()) {
                System.out.printf(Locale.ROOT, "  %-40s %s%n", each.getKey(), each.getValue());
            }
        }
        return report.problems().isEmpty() ? 0 : 1;
    }

    /**
     * What every derived instance is a fraction of, keyed by {@code type|source}.
     *
     * <p>{@code parent_event} names the instance a rider was derived from, so {@code base / parent.damage} is the rate that
     * rule applied. A rule's rate is a constant - it comes from a parameter row - so a group with two different rates is
     * either a mis-stated parent or a rate that moves; both are worth knowing before a replay tries to reproduce them.
     *
     * @param capture the parsed capture
     * @return {@code key -> distinct rates}, rounded to 1e-9
     */
    public static Map<String, java.util.TreeSet<Double>> parentRates(JsonObject capture) {
        Map<String, java.util.TreeSet<Double>> rates = new LinkedHashMap<>();
        JsonArray events = capture.getAsJsonArray("damage_events");
        if (events == null) {
            return rates;
        }
        for (JsonElement element : events) {
            JsonObject event = element.getAsJsonObject();
            if (!event.has("parent_event") || event.get("parent_event").isJsonNull()) {
                continue;
            }
            int parent = event.get("parent_event").getAsInt();
            JsonObject source = null;
            for (JsonElement candidate : events) {
                JsonObject each = candidate.getAsJsonObject();
                if (each.has("event_index") && each.get("event_index").getAsInt() == parent) {
                    source = each;
                    break;
                }
            }
            if (source == null) {
                continue;
            }
            double parentDamage = source.has("damage") ? source.get("damage").getAsDouble() : 0;
            if (parentDamage == 0) {
                continue;
            }
            JsonObject zones = event.getAsJsonObject("zones");
            double base = zones.has("damage_value") ? zones.get("damage_value").getAsDouble()
                    : zones.get("raw_damage_before_modifiers").getAsDouble();
            String kind = event.has("type") ? event.get("type").getAsString() : "?";
            JsonObject rule = event.has("rule") && !event.get("rule").isJsonNull()
                    ? event.getAsJsonObject("rule") : null;
            String named = rule != null && rule.has("source") && !rule.get("source").isJsonNull()
                    ? rule.get("source").getAsString() : "-";
            rates.computeIfAbsent(kind + "|" + named, key -> new java.util.TreeSet<>())
                    .add(Math.round(base / parentDamage * 1e9) / 1e9);
        }
        return rates;
    }

    /**
     * Whether {@code field.on_stage} equals the set of enemies that cast actually hit.
     *
     * <p>That is the whole point of the field: it must be the set a skill can <b>reach</b>, so an AoE's victims are exactly
     * it. A cast whose field lists only the aim point is the {@code targetable} shape, which is a different fact and cannot
     * drive target selection (measured on the 2026-10-30 21:20 recording: 12 of 17 casts matched, and every mismatch was an
     * AoE cast whose field held just the aim).
     *
     * @param capture the parsed capture
     * @return {@code {castsWithField, matching, mismatching, examples}}
     */
    public static Map<String, Object> onStageCheck(JsonObject capture) {
        JsonArray events = capture.getAsJsonArray("damage_events");
        Map<Integer, java.util.Set<Long>> hit = new LinkedHashMap<>();
        Map<Integer, java.util.Set<Long>> onStage = new LinkedHashMap<>();
        if (events != null) {
            for (JsonElement element : events) {
                JsonObject event = element.getAsJsonObject();
                if (event.get("cast_index") == null) {
                    continue;
                }
                int cast = event.get("cast_index").getAsInt();
                hit.computeIfAbsent(cast, key -> new java.util.LinkedHashSet<>())
                        .add(event.getAsJsonObject("target").get("uid").getAsLong());
                if (event.has("field") && !event.get("field").isJsonNull()) {
                    JsonObject field = event.getAsJsonObject("field");
                    if (field.has("on_stage") && field.get("on_stage").isJsonArray()
                            && !onStage.containsKey(cast)) {
                        java.util.Set<Long> uids = new java.util.LinkedHashSet<>();
                        for (JsonElement uid : field.getAsJsonArray("on_stage")) {
                            uids.add(uid.getAsLong());
                        }
                        onStage.put(cast, uids);
                    }
                }
            }
        }
        int matching = 0;
        java.util.List<String> examples = new java.util.ArrayList<>();
        for (Map.Entry<Integer, java.util.Set<Long>> each : onStage.entrySet()) {
            java.util.Set<Long> actually = hit.getOrDefault(each.getKey(), java.util.Set.of());
            if (each.getValue().equals(actually)) {
                matching++;
            } else if (examples.size() < 4) {
                examples.add("cast " + each.getKey() + " on_stage=" + each.getValue() + " hit=" + actually);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("castsWithField", onStage.size());
        report.put("matching", matching);
        report.put("mismatching", onStage.size() - matching);
        report.put("examples", examples);
        return report;
    }

    /**
     * Reads a capture from the test resources - the same loading the tool's other readers use.
     *
     * @param resource the classpath path
     * @return the parsed capture
     * @throws IOException when it cannot be read
     */
    static JsonObject fromResource(String resource) throws IOException {
        try (InputStream stream = RuleAudit.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("missing resource " + resource);
            }
            return com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
