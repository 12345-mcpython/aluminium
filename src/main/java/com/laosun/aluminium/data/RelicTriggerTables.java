package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.TriggerTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads relic-set trigger rules from {@code resources/relic_sets/<setId>.json}.
 *
 * <h2>Why relic abilities are trigger rules and not a second mechanism</h2>
 * Most relic 4-piece bonuses are not stats: they are exactly "when &lt;event&gt;, do &lt;something the
 * engine can already do&gt;" ("at the start of the battle, immediately regenerates 1 Skill Point").
 * That is what {@link TriggerTable} already is — the same JSON shape, the same
 * {@code TriggerInterpreter} op vocabulary, the same condition DSL as
 * {@code resources/characters/<cid>.json}. Relic sets therefore reuse all of it and add no new
 * interpreter, no new op and no per-set Java class. A set that needs a capability the vocabulary does
 * not have is <b>not approximated</b>: it is registered in {@code _unmodelled.json} and recorded as a
 * gap in {@code DOC_VS_CODE.md} §F.
 *
 * <h2>The file format</h2>
 * A set file is a JSON object keyed by the <b>worn piece count</b> the rules under it need, so that a
 * set with both a 2-piece and a 4-piece ability can state which is which:
 * <pre>{@code
 * {
 *   "2": [ { "on": "...", "when": [...], "do": [...], "source": "...", "note": "..." } ],
 *   "4": [ { ... } ]
 * }
 * }</pre>
 * The keys mirror {@code relic_sets.json}'s {@code effects[].require}, which is why
 * {@link TriggerSpec} itself did <b>not</b> grow a relic-only field: the threshold belongs to the
 * grouping, not to the rule, and a rule file is only ever read as a whole.
 *
 * <p>The thresholds of one set are compiled into <b>cumulative</b> tables: {@link Rules#at(int)}
 * for a suit wearing 4 pieces returns the 2-piece rules <em>and</em> the 4-piece rules, matching how
 * the game stacks the tiers. A suit one piece short of a threshold simply gets nothing extra.
 *
 * <h2>Lazy, cached, and loud about malformed files</h2>
 * Shaped like {@link TriggerTables} and {@link RelicSets}: a file is read the first time that set is
 * asked for, and a <b>missing file is an empty rule set rather than an error</b> — most sets have no
 * rules written, and a fresh checkout has no generated {@code relic_sets.json} at all. A file that
 * <b>exists</b> is a different matter: its thresholds, events, conditions and ops are all validated
 * while it is read, so a typo fails at load instead of silently never firing. The same is true of the
 * {@code _unmodelled.json} registry, whose every entry must carry a reason.
 */
public final class RelicTriggerTables {

    /** Directory holding the per-set files, relative to the classpath. */
    private static final String DIR = "relic_sets";

    /**
     * The registry of ability-only bonuses this vocabulary cannot express yet.
     *
     * <p>Exists so that "no rule file was written" and "an author forgot" are different states: every
     * ability-only bonus in {@code relic_sets.json} is either covered by a set file or listed here
     * with the capability it is missing, and {@code RelicTriggerTableTest} pins that partition.
     * A leading underscore keeps it from ever colliding with a numeric set id.
     */
    public static final String UNMODELLED_RESOURCE = "/" + DIR + "/_unmodelled.json";

    /** One set file is {@code {"<piece count>": [ ...rules... ]}}. */
    private static final Type FILE_SHAPE = new TypeToken<Map<String, List<TriggerSpec>>>() {
    }.getType();

    /** The registry is {@code {"<set id>": [ {require, ability, reason} ]}}. */
    private static final Type UNMODELLED_SHAPE = new TypeToken<Map<String, List<Unmodelled>>>() {
    }.getType();

    /** Cached because a set asked for twice must not hit the classpath twice. */
    private static final Map<Integer, Rules> CACHE = new HashMap<>();

    /** Gson instance for this loader (stateless and cheap; see {@link TriggerTables}). */
    private static final Gson GSON = new Gson();

    /** How many times a set file has actually been read (not merely asked for). */
    private static int loadCount;

    /** The parsed {@code _unmodelled.json}, read once. */
    private static volatile List<Unmodelled> unmodelled;

    private RelicTriggerTables() {
    }

    /**
     * The trigger rules of one relic set, by threshold.
     *
     * @param setId        the set id the rules were written for
     * @param byThreshold  threshold → the <b>cumulative</b> table for wearing at least that many pieces
     */
    public record Rules(int setId, NavigableMap<Integer, TriggerTable> byThreshold) {

        /**
         * The rules a suit wearing {@code wornPieces} pieces of this set satisfies.
         *
         * @param wornPieces how many pieces of the set are worn
         * @return the cumulative table for the highest threshold that is met, or
         *         {@link TriggerTable#EMPTY} when no threshold is met (including an empty rule set)
         */
        public TriggerTable at(int wornPieces) {
            Map.Entry<Integer, TriggerTable> met = byThreshold.floorEntry(wornPieces);
            return met == null ? TriggerTable.EMPTY : met.getValue();
        }

        /** Whether no rules were written for this set. */
        public boolean isEmpty() {
            return byThreshold.isEmpty();
        }

        /** The thresholds the set's file declares, in ascending order. */
        public Set<Integer> thresholds() {
            return byThreshold.keySet();
        }
    }

    /**
     * One ability-only relic bonus that the current op vocabulary cannot express.
     *
     * @param setId   the relic set the ability belongs to
     * @param require the piece count of the bonus (2 or 4)
     * @param ability the client's ability name, exactly as {@code relic_sets.json} spells it
     * @param reason  which capability is missing — never blank, because a gap without a reason is
     *                indistinguishable from a forgotten bonus
     */
    public record Unmodelled(int setId, int require, String ability, String reason) {
    }

    /**
     * The rules of a relic set.
     *
     * @param setId the set id
     * @return the rules, or an empty rule set when the set has no file
     * @throws IllegalStateException when a file exists but cannot be parsed into valid rules
     */
    public static Rules of(int setId) {
        synchronized (CACHE) {
            Rules cached = CACHE.get(setId);
            if (cached != null) {
                return cached;
            }
            Rules loaded = load(setId);
            CACHE.put(setId, loaded);
            return loaded;
        }
    }

    /**
     * Whether a set has a hand-written rule file at all.
     *
     * <p>Cheap-ish (one classpath lookup, then cached by the JVM). Useful for diagnostics and for a
     * test that wants to talk about coverage rather than behaviour.
     */
    public static boolean exists(int setId) {
        return RelicTriggerTables.class.getResource(pathFor(setId)) != null;
    }

    /** How many times a set file has been read from the classpath. */
    public static int loadCount() {
        synchronized (CACHE) {
            return loadCount;
        }
    }

    /**
     * The registry of abilities this vocabulary cannot express yet, every entry carrying its reason.
     *
     * <p>Read once, like a set file. A <b>missing</b> registry is an empty list (the same
     * "absence is an ordinary state" rule as a missing set file); a malformed one throws.
     *
     * @return the entries, in file order
     */
    public static List<Unmodelled> unmodelled() {
        List<Unmodelled> local = unmodelled;
        if (local != null) {
            return local;
        }
        synchronized (RelicTriggerTables.class) {
            if (unmodelled == null) {
                unmodelled = readUnmodelled();
            }
            return unmodelled;
        }
    }

    /**
     * Drops the caches. For tests that need a clean slate after changing a file on disk; the engine
     * itself never calls this.
     */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
            loadCount = 0;
        }
        synchronized (RelicTriggerTables.class) {
            unmodelled = null;
        }
    }

    /**
     * Turns a parsed set file into the engine's rules-by-threshold table, validating every key.
     *
     * <p>Public rather than private so that the rejections can be tested on a hand-made map instead
     * of a doctored copy of a shipped file — the same reason
     * {@link TriggerTable}'s constructor is where trigger specs are validated.
     *
     * @param setId  the set the rules belong to
     * @param parsed the parsed file, possibly {@code null} (an empty file)
     * @param origin where the map came from, for error messages (a resource path)
     * @return the rules, with cumulative tables built per threshold
     * @throws IllegalStateException    when a key is not a positive number, a threshold has no rules,
     *                                  or the set does not declare a bonus at that threshold
     * @throws IllegalArgumentException when a rule itself is invalid (unknown or unwired event,
     *                                  malformed condition, unknown op) — {@link TriggerTable}'s own
     *                                  contract, and what {@link #load} turns into an
     *                                  {@code IllegalStateException} naming the file
     */
    public static Rules parse(int setId, Map<String, List<TriggerSpec>> parsed, String origin) {
        NavigableMap<Integer, TriggerTable> byThreshold = new TreeMap<>();
        if (parsed == null) {
            return new Rules(setId, byThreshold);
        }
        // Ascending order, so each threshold's table can be built from the previous one: "at least N
        // pieces" means every lower tier applies as well.
        Map<Integer, List<TriggerSpec>> sorted = new TreeMap<>();
        parsed.forEach((key, specs) -> sorted.put(parseThreshold(key, origin), specs));
        List<TriggerSpec> cumulative = new ArrayList<>();
        for (Map.Entry<Integer, List<TriggerSpec>> entry : sorted.entrySet()) {
            requireDeclaredThreshold(setId, entry.getKey(), origin);
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalStateException(origin + ": threshold \"" + entry.getKey()
                        + "\" declares no rules; either write them or delete the key (an empty list "
                        + "silently means \"this bonus does nothing\")");
            }
            cumulative.addAll(entry.getValue());
            // Construction validates every rule (unknown event, unwired event, malformed condition,
            // unknown op); a bad file therefore fails here rather than at battle time.
            // The table's `cid` is 0: it is informational (the engine never branches on it), and these
            // rules belong to a relic set rather than to a character.
            byThreshold.put(entry.getKey(), new TriggerTable(0, List.copyOf(cumulative)));
        }
        return new Rules(setId, byThreshold);
    }

    /**
     * Parses one threshold key.
     *
     * @throws IllegalStateException when the key is not a positive integer
     */
    private static int parseThreshold(String key, String origin) {
        int threshold;
        try {
            threshold = Integer.parseInt(key == null ? "" : key.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(origin + ": piece-count key is not a number: \"" + key + "\"", e);
        }
        if (threshold <= 0) {
            throw new IllegalStateException(origin + ": piece-count key must be positive but was \""
                    + key + "\"");
        }
        return threshold;
    }

    /**
     * Checks that the set really has a bonus at this piece count.
     *
     * <p>This is the guard against the cheapest possible typo — {@code "4"}: [ … ] on a set whose only
     * ability is a 2-piece one — which would otherwise produce a rule that can never fire, because no
     * suit can wear four pieces of a planar ornament set.
     *
     * <p>Skipped when {@code relic_sets.json} is absent (the generated data directory is not in the
     * repository, so "no set table" is an ordinary state): there is nothing to check against, and
     * failing every set file in a fresh checkout would be worse than not checking at all.
     */
    private static void requireDeclaredThreshold(int setId, int threshold, String origin) {
        RelicSet set = RelicSets.table().get(setId);
        if (set == null) {
            return;
        }
        for (RelicSet.Effect effect : set.effects()) {
            if (effect.require() == threshold) {
                return;
            }
        }
        throw new IllegalStateException(origin + ": set " + setId + " (" + nameOf(set)
                + ") has no " + threshold + "-piece bonus; its tiers are " + requiresOf(set));
    }

    private static String nameOf(RelicSet set) {
        return set.name() == null || set.name().english() == null ? "unnamed" : set.name().english();
    }

    private static String requiresOf(RelicSet set) {
        return set.effects().stream().map(effect -> String.valueOf(effect.require())).toList().toString();
    }

    private static Rules load(int setId) {
        String path = pathFor(setId);
        try (InputStream stream = RelicTriggerTables.class.getResourceAsStream(path)) {
            if (stream == null) {
                return new Rules(setId, new TreeMap<>());   // no rules written for this set -- an ordinary state
            }
            synchronized (CACHE) {
                loadCount++;
            }
            Map<String, List<TriggerSpec>> parsed;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                parsed = GSON.fromJson(reader, FILE_SHAPE);
            }
            return parse(setId, parsed, path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read relic trigger rules " + path, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid relic trigger rules in " + path + ": " + e.getMessage(), e);
        }
    }

    private static List<Unmodelled> readUnmodelled() {
        try (InputStream stream = RelicTriggerTables.class.getResourceAsStream(UNMODELLED_RESOURCE)) {
            if (stream == null) {
                return List.of();
            }
            Map<String, List<Unmodelled>> parsed;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                parsed = GSON.fromJson(reader, UNMODELLED_SHAPE);
            }
            return indexUnmodelled(parsed, UNMODELLED_RESOURCE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + UNMODELLED_RESOURCE, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid " + UNMODELLED_RESOURCE + ": " + e.getMessage(), e);
        }
    }

    /**
     * Turns the registry's {@code "set id" → entries} map into a flat, validated list.
     *
     * <p>Public for the same reason as {@link #parse}: the rejections must be testable on hand-made
     * data. Every entry is required to name a set, a piece count, an ability and a <b>reason</b> —
     * an entry without a reason would be a gap nobody can act on.
     *
     * @param parsed the parsed registry, possibly {@code null}
     * @param origin where it came from, for error messages
     * @return the entries, in file order
     * @throws IllegalStateException on a non-numeric set id key or an incomplete entry
     */
    public static List<Unmodelled> indexUnmodelled(Map<String, List<Unmodelled>> parsed, String origin) {
        if (parsed == null) {
            return List.of();
        }
        Map<Integer, List<Unmodelled>> bySet = new LinkedHashMap<>();
        parsed.forEach((key, entries) -> {
            int setId;
            try {
                setId = Integer.parseInt(key == null ? "" : key.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(origin + ": set id key is not a number: \"" + key + "\"", e);
            }
            if (entries == null) {
                return;
            }
            for (Unmodelled entry : entries) {
                if (entry == null) {
                    continue;
                }
                if (entry.require() <= 0) {
                    throw new IllegalStateException(origin + ": set " + setId
                            + " has an entry with a non-positive piece count (" + entry.require() + ")");
                }
                if (entry.ability() == null || entry.ability().isBlank()) {
                    throw new IllegalStateException(origin + ": set " + setId + " (" + entry.require()
                            + " pieces) has an entry without an ability name");
                }
                if (entry.reason() == null || entry.reason().isBlank()) {
                    throw new IllegalStateException(origin + ": set " + setId + " ability " + entry.ability()
                            + " has no reason; a gap without a reason is indistinguishable from a forgotten bonus");
                }
                bySet.computeIfAbsent(setId, id -> new ArrayList<>())
                        .add(new Unmodelled(setId, entry.require(), entry.ability(), entry.reason()));
            }
        });
        List<Unmodelled> flat = new ArrayList<>();
        bySet.values().forEach(flat::addAll);
        return List.copyOf(flat);
    }

    private static String pathFor(int setId) {
        return "/" + DIR + "/" + setId + ".json";
    }
}
