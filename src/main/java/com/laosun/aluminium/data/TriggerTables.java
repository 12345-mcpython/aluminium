package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.TriggerTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads character trigger tables from {@code resources/characters/<cid>.json} (P8-7).
 *
 * <p>This is the "character content is data" side of the P8-0 three-way split: a character's
 * mechanics live in a JSON file next to the code, and the engine only interprets them.
 *
 * <h2>Why this is not in {@code Constant}</h2>
 * Everything in {@code Constant} is generated data under {@code data/}, loaded eagerly in a static
 * block (except {@code stage.json}, which has its own lazy holder). Character content is different
 * in three ways, so it gets its own registry:
 * <ul>
 *   <li>it is <b>hand-written</b>, not produced by the generator;</li>
 *   <li>it lives in {@code characters/}, not {@code data/} (which is gitignored);</li>
 *   <li>it is <b>optional per character</b> — {@code data/} files are all-or-nothing, while here
 *       "this character is not data-ised yet" is the normal case.</li>
 * </ul>
 *
 * <h2>Lazy, and tolerant of absence</h2>
 * A table is read the first time that cid is asked for (character construction is not on the
 * critical path of most tests, and most characters have no file). A <b>missing file is not an
 * error</b>: it yields {@link TriggerTable#EMPTY}, matching "unregistered = empty table". A file
 * that exists but is <b>invalid</b> is a different matter and throws, so a typo cannot pass
 * unnoticed.
 */
public final class TriggerTables {

    /**
     * Directory holding the per-character files, relative to the classpath.
     */
    private static final String DIR = "characters";

    /**
     * Cached because a cid asked for twice must not hit the classpath twice.
     *
     * <p>Synchronized because the map is long-lived and shared; in practice a battle runs on one
     * thread, but nothing guarantees that for the first lookup.
     */
    private static final Map<Integer, TriggerTable> CACHE = new HashMap<>();

    /**
     * Gson instance for this loader.
     *
     * <p>Deliberately its own rather than {@code JSONReader}'s: that one is bound to the {@code /data/}
     * directory of generated game data, while character content is hand-written and lives under
     * {@code /characters/}. Gson is stateless and cheap to construct.
     */
    private static final Gson GSON = new Gson();

    /**
     * How many times a file has actually been read (not merely asked for).
     *
     * <p>Exists so a test can assert the "lazy" and "cached" properties without resorting to
     * reflection: the count is observable, and it must not grow on a repeated lookup.
     */
    private static int loadCount;

    private TriggerTables() {
    }

    /**
     * The trigger table for a character.
     *
     * @param cid the character id
     * @return the table, or {@link TriggerTable#EMPTY} when the character has no file
     * @throws IllegalStateException when a file exists but cannot be parsed into a valid table
     */
    public static TriggerTable of(int cid) {
        synchronized (CACHE) {
            TriggerTable cached = CACHE.get(cid);
            if (cached != null) {
                return cached;
            }
            TriggerTable loaded = load(cid);
            CACHE.put(cid, loaded);
            return loaded;
        }
    }

    /**
     * Whether a character has a hand-written trigger file at all.
     *
     * <p>Cheap-ish (one classpath lookup, then cached). Useful for diagnostics and for tests that
     * want to assert coverage rather than behaviour.
     */
    public static boolean exists(int cid) {
        return resourceFor(cid) != null;
    }

    /**
     * How many times a trigger file has been read from the classpath. See {@link #loadCount}.
     */
    public static int loadCount() {
        synchronized (CACHE) {
            return loadCount;
        }
    }

    /**
     * Drops the cache. For tests that need to re-read a file they just changed, and for callers that
     * want a clean slate; not used by the engine itself.
     */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
            loadCount = 0;
        }
    }

    private static TriggerTable load(int cid) {
        String path = resourceFor(cid);
        if (path == null) {
            return TriggerTable.EMPTY;          // not data-ised yet -- an ordinary state
        }
        try (InputStream stream = TriggerTables.class.getResourceAsStream(path)) {
            if (stream == null) {
                return TriggerTable.EMPTY;
            }
            synchronized (CACHE) {
                loadCount++;
            }
            List<TriggerSpec> specs;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                specs = GSON.fromJson(reader, new TypeToken<List<TriggerSpec>>() {
                }.getType());
            }
            // Construction validates every rule (unknown event, unwired event, malformed condition,
            // unknown op); a bad file therefore fails here rather than at battle time.
            return new TriggerTable(cid, specs);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read trigger table for cid " + cid, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid trigger table for cid " + cid + " (" + path + "): " + e.getMessage(), e);
        }
    }

    private static String resourceFor(int cid) {
        String path = "/" + DIR + "/" + cid + ".json";
        return TriggerTables.class.getResource(path) == null ? null : path;
    }
}
