package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.RelicSet;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads {@code data/relic_sets.json} — the relic-set table (60 sets / 92 bonuses) that says what wearing
 * two or four pieces of a set actually does.
 *
 * <p>Shaped like {@link SkillEffects} and {@link TriggerTables}: read once, lazily, from the classpath, and
 * a <b>missing file is an empty table rather than an error</b> — the file is generator output (see the
 * README's generator section), and the engine must still be able to run without it; it simply cannot apply
 * set bonuses, which is exactly the state before this table existed.
 *
 * <p>The file's top level is a map keyed by the set id as a <b>string</b>, which
 * {@link com.laosun.aluminium.utils.JSONReader#fromJSON(String, Class)} cannot express; hence the
 * {@code TypeToken} map here rather than a bean for the whole file.
 *
 * <h2>What is validated while reading, and why it throws instead of tolerating</h2>
 * <ul>
 *   <li>a key that is not an integer is rejected (the table is keyed by {@code int} set id in the engine);</li>
 *   <li>a row whose {@code set_id} disagrees with its own map key is rejected. This is the guard against
 *       the failure mode this project keeps hitting: a misspelled {@code @SerializedName} makes Gson bind
 *       {@code setId} to {@code 0} for every row, and because the id is then never used as a lookup key
 *       nothing would visibly break — the set bonuses would just quietly stop matching. Comparing the two
 *       turns that into an exception at startup;</li>
 *   <li>absent {@code parts} / {@code effects} / {@code properties} become empty lists, so no caller has to
 *       null-check (the same normalisation {@code Constant} does for monster rows).</li>
 * </ul>
 */
public final class RelicSets {

    private static final String RESOURCE = "/data/relic_sets.json";

    /** The file is {@code {"<set id>": {...set...}}}. */
    private static final Type SHAPE = new TypeToken<Map<String, RelicSet>>() {
    }.getType();

    private static volatile Map<Integer, RelicSet> table;

    private static final AtomicInteger LOAD_COUNT = new AtomicInteger();

    private RelicSets() {
    }

    /**
     * The whole table, keyed by set id (casting the JSON map's string keys).
     *
     * <p>Shaped like {@link com.laosun.aluminium.Constant#stages()}: the first caller pays for the read,
     * everybody else gets the same immutable map. {@code Constant.RELIC_SETS} is the usual way in.
     *
     * @return set id → set; empty when the data file was not generated
     */
    public static Map<Integer, RelicSet> table() {
        Map<Integer, RelicSet> local = table;
        if (local != null) {
            return local;
        }
        synchronized (RelicSets.class) {
            if (table == null) {
                table = read();
            }
            return table;
        }
    }

    /**
     * Looks a set up by id, refusing to return {@code null}.
     *
     * <p>Used by the relic pipeline: a relic that names a set the table does not contain is a bug in
     * whoever built the relic (or a stale data file), and treating it as "no bonus" would hide it.
     *
     * @param setId the set id
     * @return the set
     * @throws IllegalArgumentException when no such set exists
     */
    public static RelicSet require(int setId) {
        RelicSet set = table().get(setId);
        if (set == null) {
            throw new IllegalArgumentException(table().isEmpty()
                    ? "relic set data not loaded (relic_sets.json is generator output; see the generator "
                    + "section of the README), so set " + setId + " cannot be resolved"
                    : "unknown relic set id: " + setId);
        }
        return set;
    }

    /** How many times the resource was actually read — lets a test assert "read once". */
    public static int loadCount() {
        return LOAD_COUNT.get();
    }

    private static Map<Integer, RelicSet> read() {
        LOAD_COUNT.incrementAndGet();
        try (InputStream in = RelicSets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return index(new Gson().fromJson(reader, SHAPE));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    /**
     * Turns the file's {@code "set id" → set} map into the engine's {@code int id → set} table.
     *
     * <p>Everything the table is allowed to contain is decided here: the key must be a number, and a row
     * whose own {@code set_id} disagrees with the key it is filed under is rejected — that disagreement is
     * what a failed {@code set_id} binding looks like (every row would say {@code 0}), and it is silent
     * otherwise, because the engine looks sets up by the map key and never re-reads the field.
     *
     * <p>Public rather than private so that both rejections can be tested with a hand-made map instead of a
     * doctored copy of a generated data file — the same reason {@code TriggerTable}'s constructor is where
     * trigger specs are validated.
     *
     * @param parsed the parsed file, possibly {@code null} (an empty file)
     * @return the engine-facing table, with every row normalised so its lists are non-null
     * @throws IllegalStateException when a key is not a number or a row disagrees with its key
     */
    public static Map<Integer, RelicSet> index(Map<String, RelicSet> parsed) {
        if (parsed == null) {
            return Map.of();
        }
        Map<Integer, RelicSet> byId = new LinkedHashMap<>();
        parsed.forEach((key, set) -> {
            if (set == null) {
                return;
            }
            int id = parseKey(key);
            if (set.setId() != id) {
                throw new IllegalStateException(RESOURCE + ": row \"" + key + "\" declares set_id "
                        + set.setId() + " — the two must agree (a mismatch usually means set_id "
                        + "failed to bind)");
            }
            byId.put(id, normalize(set));
        });
        return Map.copyOf(byId);
    }

    private static int parseKey(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(RESOURCE + ": set id key is not a number: \"" + key + "\"", e);
        }
    }

    /**
     * Makes the lists non-null so that no caller has to care whether the generator emitted an empty array
     * or left the field out. Anything the engine cannot express is <b>left in place</b> (a property with an
     * unknown name is not dropped here — it throws when it is applied).
     */
    private static RelicSet normalize(RelicSet set) {
        List<RelicSet.Part> parts = set.parts() == null ? List.of() : List.copyOf(set.parts());
        List<RelicSet.Effect> effects = set.effects() == null ? List.of()
                : set.effects().stream().map(effect -> new RelicSet.Effect(
                        effect.require(),
                        effect.desc(),
                        effect.param() == null ? List.of() : List.copyOf(effect.param()),
                        effect.properties() == null ? List.of() : List.copyOf(effect.properties()),
                        effect.ability())).toList();
        return new RelicSet(set.setId(), set.name(), set.releaseVersion(), parts, effects, set.isPlanar());
    }
}
