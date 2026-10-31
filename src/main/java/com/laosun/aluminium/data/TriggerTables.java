package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.ResourceSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.trigger.TriggerTable;

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
 * Loads character trigger tables from {@code resources/game/characters/<cid>.json}.
 *
 * <p>This is the "character content is data" side of the three-way split: a character's
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
 * with a {@code rules} array plus the character's {@code resources} declarations:
 *
 * <pre>
 * [ { "on": "SKILL_CAST", ... } ]                     // rules only
 * { "schema": 1, "character": 1001,
 *   "resources": [ { "id": "充能", "max": 3 } ],
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
 *
 * <h2>Annotations are not runtime fields</h2>
 * Keys beginning with {@code _} ({@code _note}, {@code _source}, {@code _status}, {@code _gaps},
 * {@code _divergences}, {@code _changelog}, {@code _literal_reason}, {@code _comment}) are
 * <b>annotations</b>: the loader accepts them at file, rule and effect level and they never reach a
 * bean. They are the migration path away from the legacy {@code note} / {@code source} strings,
 * which stay readable because old files use them. An annotation is still <b>not silently ignored</b>:
 * only the exact names above are accepted, so {@code _notee} is refused like any other unknown key.
 *
 * <p>Files may also carry line comments and C-style block comments (Gson's lenient reader). Comments make
 * a source file annotatable without changing its JSON shape; the loader still refuses every key it does
 * not know. A trailing comma is <b>not</b> accepted - Gson's lenient mode allows comments, not that.
 *
 * <h2>Version and owner</h2>
 * {@code schema} is the file-format version and {@code character} the owning cid. Both are optional
 * (a legacy file states neither), but a stated schema must be the one this engine reads and a stated
 * character must match the file it is in - a file copied under another cid would otherwise load and
 * apply its rules to the wrong character.
 */
public final class TriggerTables {

    /**
     * Directory holding the per-character files, relative to the classpath.
     */
    private static final String DIR = "game/characters";

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
     * The format version this loader reads. A file may omit {@code schema} (a legacy file, version 0);
     * a file that states it must state this number.
     */
    public static final int CURRENT_SCHEMA = 1;

    /**
     * Keys that are <b>annotations, never runtime fields</b>: accepted at file, rule and effect level and
     * dropped before any bean sees them. The leading underscore is what makes "this is not executed" visible
     * in the file itself.
     *
     * <p>The set is deliberately closed: a mistyped annotation ({@code _notee}) is still an unknown key.
     */
    public static final Set<String> ANNOTATION_KEYS = Set.of(
            "_note", "_source", "_status", "_gaps", "_divergences", "_changelog", "_literal_reason", "_comment");

    /**
     * The older spellings of the same facts. They stay allowed (hundreds of shipped rules use them) but a
     * new annotation should use the underscore form - only the underscore form is guaranteed to stay out of
     * the runtime objects.
     */
    private static final Set<String> LEGACY_ANNOTATION_KEYS = Set.of("note", "source");

    /**
     * The keys the object form may carry. Anything else is refused - see the class docs.
     */
    private static final Set<String> OBJECT_KEYS = withAnnotations("resources", "rules", "schema", "character");

    private static Set<String> withAnnotations(String... keys) {
        Set<String> all = new java.util.TreeSet<>(List.of(keys));
        all.addAll(ANNOTATION_KEYS);
        all.addAll(LEGACY_ANNOTATION_KEYS);
        return Set.copyOf(all);
    }

    /**
     * The annotation keys a trigger file may carry at any level: the underlined annotations plus the two legacy
     * spellings ({@code note} / {@code source}).
     *
     * <p>Exposed so the light-cone and relic-set loaders accept <b>exactly</b> the same vocabulary as a character
     * file. A second hand-written list would be a second answer to "is this key an annotation?", and the two would
     * drift the day one of them gains a name.
     *
     * @return the accepted annotation keys
     */
    public static Set<String> annotationKeys() {
        return withAnnotations();
    }

    /**
     * The keys a predicate object may carry; see {@link com.laosun.aluminium.beans.PredicateSpec}. Its
     * {@code json} map is the entity vocabulary's, checked when the predicate is compiled.
     */
    private static final Set<String> PREDICATE_KEYS = Set.of("type", "terms", "term", "ref", "entity", "json");

    /**
     * The machine-readable rule statuses. A status is not prose: {@code partial} means some clauses are registered as
     * gaps, {@code registered} means none are written yet, {@code unmodelled} means the engine cannot express them.
     */
    private static final Set<String> STATUS_VALUES = Set.of("implemented", "partial", "registered", "unmodelled");

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
     * ("获得该角色即生效，无需上场"), read from `warehouse/&lt;cid&gt;.json`.
     *
     * <p>Deliberately the same parser as `characters/`: same rule keys, same effect keys, same validation, so a warehouse clause cannot
     * be written in a dialect of its own. EMPTY when the character has no warehouse file -- the ordinary case.
     *
     * <p>Note: Not cached, unlike {@link #of(int)}: a warehouse clause is read once per battle that registers the listener, and the cache
     * exists for the per-character table that is asked on every event.
     */
    public static TriggerTable warehouse(int cid) {
        return load(cid, "/game/warehouse/" + cid + ".json");
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
                // Gson's parser is lenient, which is what lets a source file carry comments (and a trailing comma):
                // the annotation lives next to the rule it explains instead of in a second file that can drift.
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonArray()) {
                    requireKnownRuleKeys(root);
                    requireKnownEffectKeys(root);
                    requireKnownConditionKeys(root);
                    requireKnownStatusAnnotations(root);
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
                    requireFileHeader(object, cid);
                    requireStatusObject(object, "status annotations in file " + cid);
                    JsonElement rules = object.get("rules");
                    if (rules == null || rules.isJsonNull()) {
                        throw new IllegalArgumentException(
                                "the object form needs a \"rules\" array; only a bare array may omit it "
                                        + "(a character with nothing but rules uses the array form)");
                    }
                    // The same guard, one level deeper. The comment on the resource check describes this exact trap --
                    // "Gson drops a key it does not know" -- but only the resource declaration was walked, and an effect
                    // writing `maxStacks` (the Java name) was accepted and then dropped, leaving a stackable state with a cap
                    // of 1. The allowed sets come from `EffectSpec`'s and `TriggerSpec`'s own `@SerializedName` annotations,
                    // so they cannot drift from what Gson maps.
                    requireKnownRuleKeys(rules);
                    requireKnownEffectKeys(rules);
                    requireKnownConditionKeys(rules);
                    requireKnownStatusAnnotations(rules);
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

    /*
     * Refuses a key Gson would silently drop.
     *
     * <p>{@code value} is either one object (the file) or an array of them (the resource declarations); an
     * array may be empty, and anything else is not a shape this file understands.
     */
    /**
     * The keys {@link EffectSpec} actually maps, read from its own annotations.
     *
     * <p>Reflection rather than a hand-kept list: the failure this guards is exactly a key the loader "knows" and Gson does
     * not, so the two must be the same source of truth.
     */
    private static final Set<String> EFFECT_KEYS = includeAnnotations(effectKeys());

    private static Set<String> effectKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : com.laosun.aluminium.beans.EffectSpec.class.getDeclaredFields()) {
            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            // Gson's own rule: an annotated field is keyed by the annotation, a plain one by the field's
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
     * The keys {@link com.laosun.aluminium.beans.TriggerSpec} actually maps, read from its own annotations.
     *
     * <p>The same reflection the effect guard uses, for the same reason: a key the loader "knows" and Gson does not is a value
     * that vanishes without a word.
     */
    private static final Set<String> RULE_KEYS = includeAnnotations(ruleKeys());

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

    /**
     * A mapped key set plus the annotation keys. Annotations are accepted where they document something but are
     * never mapped to a field, so the guard must allow them explicitly rather than learn them by reflection.
     */
    private static Set<String> includeAnnotations(Set<String> mapped) {
        Set<String> all = new java.util.TreeSet<>(mapped);
        all.addAll(ANNOTATION_KEYS);
        all.addAll(LEGACY_ANNOTATION_KEYS);
        return Set.copyOf(all);
    }

    /**
     * Walks the rules themselves: known keys, and an event to listen to.
     */
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

    /**
     * Walks every rule's {@code do} array and refuses a key Gson would silently drop.
     */
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

    /**
     * Validates the structured status annotations: {@code _status} is one of the closed values, and
     * {@code _gaps} / {@code _divergences} are string arrays. A non-empty gap list must carry a status, so
     * "this rule still has work" cannot be recorded without saying which state the rule is in.
     */
    private static void requireKnownStatusAnnotations(JsonElement rules) {
        if (rules == null || !rules.isJsonArray()) {
            return;
        }
        for (JsonElement element : rules.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rule = element.getAsJsonObject();
            requireStatusObject(rule, "status annotations in rule "
                    + (rule.has("id") ? rule.get("id").getAsString() : "(no id)"));
        }
    }

    /**
     * One structured status, at rule or file level. The file level is what a partially implemented character needs:
     * the missing clauses are not a rule of their own, so the gap belongs to the whole file.
     */
    private static void requireStatusObject(JsonObject object, String where) {
        JsonElement status = object.get("_status");
        if (status != null && !status.isJsonNull()) {
            if (!status.isJsonPrimitive() || !status.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(where + ": \"_status\" must be a string");
            }
            String value = status.getAsString().trim();
            if (!STATUS_VALUES.contains(value)) {
                throw new IllegalArgumentException(
                        where + ": unknown \"_status\": \"" + value + "\"; known: "
                                + STATUS_VALUES.stream().sorted().toList());
            }
        }
        boolean hasGap = requireStringArray(object, "_gaps", where);
        boolean hasDivergence = requireStringArray(object, "_divergences", where);
        if (status == null || status.isJsonNull()) {
            if (hasGap || hasDivergence) {
                throw new IllegalArgumentException(
                        where + ": a non-empty _gaps / _divergences list needs a \"_status\" (implemented / "
                                + "partial / registered / unmodelled)");
            }
        } else {
            String value = status.getAsString().trim();
            boolean hasWork = hasGap || hasDivergence;
            if ("implemented".equals(value) && hasWork) {
                throw new IllegalArgumentException(
                        where + ": \"_status\": \"implemented\" cannot carry a non-empty _gaps / _divergences");
            }
            if (!"implemented".equals(value) && !hasWork) {
                throw new IllegalArgumentException(
                        where + ": \"_status\": \"" + value + "\" needs a non-empty _gaps or _divergences entry");
            }
        }
    }

    /**
     * Reads one optional string array. Returns whether it is present and non-empty; a malformed shape throws.
     */
    private static boolean requireStringArray(JsonObject rule, String key, String where) {
        JsonElement value = rule.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(where + ": \"" + key + "\" must be an array of strings");
        }
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                    || entry.getAsString().isBlank()) {
                throw new IllegalArgumentException(where + ": every \"" + key + "\" entry must be a non-blank string");
            }
        }
        return value.getAsJsonArray().size() > 0;
    }

    /**
     * Walks the conditions of every rule and refuses a shape Gson would drop or the compiler would only reach
     * later with a less precise message.
     *
     * <p>This walks one list: entries are either DSL strings or predicate objects. The predicate vocabulary is
     * checked here so a typo in an object is refused before it is compiled, not only when the rule fires.
     */
    private static void requireKnownConditionKeys(JsonElement rules) {
        if (rules == null || !rules.isJsonArray()) {
            return;
        }
        for (JsonElement rule : rules.getAsJsonArray()) {
            if (!rule.isJsonObject()) {
                continue;
            }
            JsonObject object = rule.getAsJsonObject();
            JsonElement mixed = object.get("when");
            if (mixed != null && !mixed.isJsonNull()) {
                if (!mixed.isJsonArray()) {
                    throw new IllegalArgumentException("\"when\" must be an array of conditions");
                }
                for (JsonElement entry : mixed.getAsJsonArray()) {
                    if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                        continue;
                    }
                    if (!entry.isJsonObject()) {
                        throw new IllegalArgumentException(
                                "a \"when\" entry must be a condition string or a predicate object, got " + entry);
                    }
                    requireKnownPredicateKeys(entry);
                }
            }
        }
    }

    /**
     * One predicate object, recursively: known keys only, with {@code terms} / {@code term} walked as predicates.
     * The {@code json} map is an entity predicate's own vocabulary and is checked when it is compiled.
     */
    private static void requireKnownPredicateKeys(JsonElement predicate) {
        if (!predicate.isJsonObject()) {
            throw new IllegalArgumentException("a predicate must be a JSON object, got " + predicate);
        }
        JsonObject object = predicate.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!PREDICATE_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "unknown key \"" + key + "\" in a predicate; known: "
                                + PREDICATE_KEYS.stream().sorted().toList());
            }
        }
        JsonElement terms = object.get("terms");
        if (terms != null && !terms.isJsonNull()) {
            if (!terms.isJsonArray()) {
                throw new IllegalArgumentException("\"terms\" must be an array of predicates");
            }
            for (JsonElement term : terms.getAsJsonArray()) {
                requireKnownPredicateKeys(term);
            }
        }
        JsonElement term = object.get("term");
        if (term != null && !term.isJsonNull()) {
            requireKnownPredicateKeys(term);
        }
    }

    /**
     * The optional file header: {@code schema} and {@code character}.
     *
     * <p>Both are checked rather than ignored because each has a silent failure of its own: an unknown schema is
     * a file written for a format this engine does not read, and a character id that does not match the file's
     * own cid is a rule set copied under the wrong name - the rules would load and apply to the wrong character.
     */
    private static void requireFileHeader(JsonObject object, int cid) {
        requireFileHeader(object, "character", cid, "character " + cid);
    }

    /**
     * The shared file header: {@code schema} and the owning id.
     *
     * <p>The same two checks for every trigger file, whichever kind it is: a character states {@code character},
     * a light cone {@code light_cone}, a relic set {@code relic_set}. Stating the wrong kind's key is an unknown key
     * (the loader's allowed-set check), so a copied file cannot arrive under the wrong owner.
     *
     * @param object   the file root
     * @param ownerKey the key that names the owner ({@code character} / {@code light_cone} / {@code relic_set})
     * @param ownerId  the id the file is being loaded as
     * @param origin   what is being loaded, for error messages (e.g. {@code "light cone 23055"})
     * @throws IllegalArgumentException when the schema is not the one this engine reads, is not a whole number, or the
     *                                  stated owner does not match the id being loaded
     */
    public static void requireFileHeader(JsonObject object, String ownerKey, int ownerId, String origin) {
        JsonElement schema = object.get("schema");
        if (schema != null && !schema.isJsonNull()) {
            if (!schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(
                        origin + ": \"schema\" must be a whole number, got " + schema + " (this engine reads "
                                + CURRENT_SCHEMA + "; omit the key for a legacy file)");
            }
            double version = schema.getAsDouble();
            if (version != Math.rint(version)) {
                throw new IllegalArgumentException(
                        origin + ": \"schema\" must be a whole number, got " + schema);
            }
            if ((int) version != CURRENT_SCHEMA) {
                throw new IllegalArgumentException(
                        origin + ": this file states \"schema\": " + schema + ", but this engine reads version "
                                + CURRENT_SCHEMA + "; a newer file needs a newer engine, and an older one needs "
                                + "migrating (omit the key for a legacy file)");
            }
        }
        JsonElement owner = object.get(ownerKey);
        if (owner != null && !owner.isJsonNull()) {
            if (!owner.isJsonPrimitive()) {
                throw new IllegalArgumentException(
                        origin + ": \"" + ownerKey + "\" must be the owning id, got " + owner);
            }
            String declared = owner.getAsString().trim();
            if (!declared.equals(String.valueOf(ownerId))) {
                throw new IllegalArgumentException(
                        origin + ": this file declares \"" + ownerKey + "\": " + declared + ", but it is loaded as id "
                                + ownerId + "; one file, one owner (a copied rule set would otherwise apply to the "
                                + "wrong content)");
            }
        }
    }

    /**
     * Walks the rules of one file with the same vocabulary guards a character file gets: known rule keys, known
     * effect keys, known condition keys, and the structured status annotations.
     *
     * <p>Exposed for the light-cone and relic-set loaders. Until 2026-10-31 they parsed their rules straight into
     * {@code TriggerSpec}, so a mistyped key ({@code _notee}) or a status the engine does not know was silently
     * dropped there - a character file refused exactly the same text.
     *
     * @param rules  the rule array (may be {@code null}, in which case nothing is checked)
     * @param origin what is being loaded, for error messages
     */
    public static void requireRuleVocabulary(JsonElement rules, String origin) {
        if (rules == null || rules.isJsonNull()) {
            return;
        }
        try {
            requireKnownRuleKeys(rules);
            requireKnownEffectKeys(rules);
            requireKnownConditionKeys(rules);
            requireKnownStatusAnnotations(rules);
        } catch (IllegalArgumentException | IllegalStateException refusal) {
            throw new IllegalArgumentException(origin + ": " + refusal.getMessage(), refusal);
        }
    }

    /**
     * Validates the file-level status annotations ({@code _status} / {@code _gaps} / {@code _divergences}).
     *
     * @param file   the file root
     * @param origin what is being loaded, for error messages
     */
    public static void requireFileStatus(JsonObject file, String origin) {
        try {
            requireStatusObject(file, "status annotations in " + origin);
        } catch (IllegalArgumentException refusal) {
            throw new IllegalArgumentException(origin + ": " + refusal.getMessage(), refusal);
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
