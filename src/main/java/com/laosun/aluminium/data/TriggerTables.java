package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.TriggerTable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads character trigger tables from {@code resources/characters/<cid>.json} (P8-).
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
 *   <li>it is <b>optional per character</b> - {@code data/} files are all-or-nothing, while here
 *       "this character is not data-ised yet" is the normal case.</li>
 * </ul>
 *
 * <h2>Lazy, and tolerant of absence</h2>
 * A table is read the first time that cid is asked for (character construction is not on the
 * critical path of most tests, and most characters have no file). A <b>missing file is not an
 * error</b>: it yields {@link TriggerTable#EMPTY}, matching "unregistered = empty table". A file
 * that exists but is <b>invalid</b> is a different matter and throws, so a typo cannot pass
 * unnoticed.
 *
 * <h2>Two shapes, one of them the rule list</h2>
 * A file is either a bare <b>array</b> of rules - what every file written before resources existed
 * looks like, and still exactly what a character with nothing but rules needs - or an <b>object</b>
 * with a {@code rules} array plus the character's {@code resources} declarations (P8-8):
 *
 * <pre>
 * [ { "on": "SKILL_CAST", ... } ]                     // rules only
 * { "resources": [ { "id": "充能", "max": 3 } ],
 *   "rules":     [ { "on": "BREAK", ... } ] }         // a character with a resource
 * </pre>
 *
 * <p><b>Why the array stays.</b> Wrapping thirteen shipped files in an object that adds nothing to
 * them would be churn with a real cost (every one of those files carries a long {@code note} block,
 * so a reformat is a diff nobody can read) and no benefit: the rule list is the whole content of a
 * file that has nothing else to declare. A file that <b>does</b> declare resources must use the
 * object form, because the declarations and the rules that read them belong in one place - the
 * resource name is spelled in both.
 *
 * <p>An object with no {@code rules} is <b>refused</b> rather than read as "no rules": the shape
 * exists to hold both halves, and a misspelled key ({@code "trigger"}) would otherwise produce a
 * character that silently does nothing. For the same reason an unknown key is refused.
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
     * The two list types this loader reads. Hoisted out of the parse so the two shapes share them.
     */
    private static final Type SPEC_LIST = new TypeToken<List<TriggerSpec>>() {
    }.getType();

    private static final Type RESOURCE_LIST = new TypeToken<List<ResourceSpec>>() {
    }.getType();

    /**
     * The keys the object form may carry. Anything else is refused - see the class docs.
     */
    private static final Set<String> OBJECT_KEYS = Set.of("resources", "rules");

    /**
     * The keys one resource declaration may carry.
     *
     * <p>Read off {@link ResourceSpec}'s own record components rather than typed out again: the two lists are
     * the same list, and a hand-written copy would refuse a valid file the day a field is added (loud, but
     * wrong) - or, worse, accept one Gson will drop.
     */
    private static final Set<String> RESOURCE_KEYS = resourceKeys();

    private static Set<String> resourceKeys() {
        Set<String> keys = new java.util.TreeSet<>();
        for (java.lang.reflect.RecordComponent component : ResourceSpec.class.getRecordComponents()) {
            com.google.gson.annotations.SerializedName name =
                    component.getAnnotation(com.google.gson.annotations.SerializedName.class);
            keys.add(name == null ? component.getName() : name.value());
        }
        return Set.copyOf(keys);
    }

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
        return load(cid, resourceFor(cid));
    }

    /**
     * [WAREHOUSE] the global-support clauses of a character that is OWNED but need not be deployed
     * ("获得该角色即生效，无需上场"), read from `warehouse/<cid>.json`.
     *
     * <p>Deliberately the same parser as `characters/`: same rule keys, same effect keys, same validation, so a warehouse clause cannot
     * be written in a dialect of its own. EMPTY when the character has no warehouse file -- the ordinary case.
     *
     * <p>Note: Not cached, unlike {@link #of(int)}: a warehouse clause is read once per battle that registers the listener, and the cache
     * exists for the per-character table that is asked on every event.
     */
    public static TriggerTable warehouse(int cid) {
        return load(cid, "/warehouse/" + cid + ".json");
    }

    private static TriggerTable load(int cid, String path) {
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
            List<ResourceSpec> resources = List.of();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonArray()) {
                    requireKnownRuleKeys(root);
                    requireKnownEffectKeys(root);
                    specs = GSON.fromJson(root, SPEC_LIST);
                } else if (root.isJsonObject()) {
                    JsonObject object = root.getAsJsonObject();
                    for (String key : object.keySet()) {
                        if (!OBJECT_KEYS.contains(key)) {
                            throw new IllegalArgumentException(
                                    "unknown key \"" + key + "\"; this file is either a bare array of "
                                            + "rules or an object with " + OBJECT_KEYS.stream().sorted()
                                            .toList());
                        }
                    }
                    JsonElement rules = object.get("rules");
                    if (rules == null || rules.isJsonNull()) {
                        throw new IllegalArgumentException(
                                "the object form needs a \"rules\" array; only a bare array may omit it "
                                        + "(a character with nothing but rules uses the array form)");
                    }
                    // The same guard, one level deeper (2026-10-02). The comment on the resource check describes this exact trap --
                    // "Gson drops a key it does not know" -- but only the resource declaration was walked, and an effect
                    // writing `maxStacks` (the Java name) was accepted and then dropped, leaving a stackable state with a cap
                    // of 1. The allowed sets come from `EffectSpec`'s and `TriggerSpec`'s own `@SerializedName` annotations,
                    // so they cannot drift from what Gson maps.
                    requireKnownRuleKeys(rules);
                    requireKnownEffectKeys(rules);
        specs = GSON.fromJson(rules, SPEC_LIST);
                    JsonElement declared = object.get("resources");
                    if (declared != null && !declared.isJsonNull()) {
                        // Checked on the raw JSON, before Gson sees it: Gson drops a key it does not know, so
                        // a misspelled "intial": 1 would be read as "starts at 0" -- a wrong number with
                        // nothing to report, which is the failure this whole class guards against. (The
                        // required "max" already catches "mx"; this catches the rest.)
                        requireKnownKeys(declared, RESOURCE_KEYS, "resource declaration");
                        // Gson builds records through their canonical constructor, so ResourceSpec's own
                        // validation runs on this path too (pinned by CharacterResourceTest).
                        resources = GSON.fromJson(declared, RESOURCE_LIST);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "the file must be a JSON array of rules or an object with \"rules\"");
                }
            }
            // Construction validates every rule (unknown event, unwired event, malformed condition,
            // unknown op); a bad file therefore fails here rather than at battle time.
            return new TriggerTable(cid, specs, resources);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read trigger table for cid " + cid, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Invalid trigger table for cid " + cid + " (" + path + "): " + reasonOf(e), e);
        }
    }

    /**
     * The most specific message in an exception's cause chain.
     *
     * <p>Needed because Gson <b>wraps</b> a failure inside a value object's constructor:
     * {@code ResourceSpec}'s own rejection of "max: 0" arrives as
     * {@code RuntimeException("Failed to invoke constructor ... with args [充能, 0, null, null, null]")}, and its
     * cause - the sentence that says <em>why</em> - is the only useful part. Reporting the wrapper alone would
     * turn a precise content error into "something went wrong with a constructor", which is the kind of message
     * that makes an author guess.
     *
     * <p>Both messages are kept when they differ: the wrapper names what was being built, the cause says what was
     * wrong with it.
     */
    private static String reasonOf(Throwable e) {
        StringBuilder reason = new StringBuilder(String.valueOf(e.getMessage()));
        Throwable cause = e.getCause();
        while (cause != null && cause != cause.getCause()) {
            reason.append(" -- caused by: ").append(cause.getMessage());
            cause = cause.getCause();
        }
        return reason.toString();
    }

    private static String resourceFor(int cid) {
        String path = "/" + DIR + "/" + cid + ".json";
        return TriggerTables.class.getResource(path) == null ? null : path;
    }

    /**
     * Refuses a key Gson would silently drop.
     *
     * <p>{@code value} is either one object (the file) or an array of them (the resource declarations); an
     * array may be empty, and anything else is not a shape this file understands.
     */
    /**
     * The keys {@link EffectSpec} actually maps, read from its own annotations (2026-10-02).
     *
     * <p>Reflection rather than a hand-kept list: the failure this guards is exactly a key the loader "knows" and Gson does
     * not, so the two must be the same source of truth.
     */
    private static final Set<String> EFFECT_KEYS = effectKeys();

    private static Set<String> effectKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : com.laosun.aluminium.beans.EffectSpec.class.getDeclaredFields()) {
            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            // Gson's own rule (2026-10-02): an annotated field is keyed by the annotation, a plain one by the field's
            // name. Collecting only the annotated ones rejected `amountFromEvent` and `amountFromAttr` -- keys the shipped
            // files use and Gson maps -- which the suite showed at once.
            keys.add(name != null ? name.value() : field.getName());
        }
        // `when`/`on`/`id` style keys never appear on an effect, and an empty set would reject every file: a reflection
        // result that came back empty is a broken build, not a validation result.
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "no @SerializedName fields were found on EffectSpec, so the effect keys cannot be checked");
        }
        return Set.copyOf(keys);
    }

    /**
     * The keys {@link com.laosun.aluminium.beans.TriggerSpec} actually maps, read from its own annotations (2026-10-02).
     *
     * <p>The same reflection the effect guard uses, for the same reason: a key the loader "knows" and Gson does not is a value
     * that vanishes without a word.
     */
    private static final Set<String> RULE_KEYS = ruleKeys();

    private static Set<String> ruleKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Field field
                : com.laosun.aluminium.beans.TriggerSpec.class.getDeclaredFields()) {
            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            keys.add(name != null ? name.value() : field.getName());
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("no fields were found on TriggerSpec, so rule keys cannot be checked");
        }
        return Set.copyOf(keys);
    }

    /** Walks the rules themselves: known keys, and an event to listen to (2026-10-02). */
    private static void requireKnownRuleKeys(JsonElement rules) {
        if (rules == null || !rules.isJsonArray()) {
            return;
        }
        for (JsonElement rule : rules.getAsJsonArray()) {
            requireKnownKeys(rule, RULE_KEYS, "rule");
            if (!rule.isJsonObject()) {
                continue;
            }
            JsonObject object = rule.getAsJsonObject();
            boolean hasEvent = object.has("on") || object.has("on_any");
            if (!hasEvent) {
                throw new IllegalArgumentException(
                        "a rule states neither \"on\" nor \"on_any\", so it would never fire (its keys: "
                                + object.keySet().stream().sorted().toList() + ")");
            }
        }
    }

    /** Walks every rule's {@code do} array and refuses a key Gson would silently drop. */
    private static void requireKnownEffectKeys(JsonElement rules) {
        if (rules == null || !rules.isJsonArray()) {
            return;
        }
        for (JsonElement rule : rules.getAsJsonArray()) {
            if (!rule.isJsonObject()) {
                continue;
            }
            JsonElement effects = rule.getAsJsonObject().get("do");
            if (effects != null && effects.isJsonArray()) {
                requireKnownKeys(effects, EFFECT_KEYS, "effect");
            }
        }
    }

    private static void requireKnownKeys(JsonElement value, Set<String> known, String what) {
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                requireKnownKeys(element, known, what);
            }
            return;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be a JSON object, not " + value.getClass()
                    .getSimpleName());
        }
        for (String key : value.getAsJsonObject().keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException(
                        "unknown key \"" + key + "\" in a " + what + "; known: "
                                + known.stream().sorted().toList());
            }
        }
    }
}
