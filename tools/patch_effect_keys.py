"""Close the hole the camelCase key fell through (2026-10-02, round 1688).

Measured last round: a content effect writing `"maxStacks"` (the Java name) was accepted by the loader and then silently dropped
by Gson, which only knows `@SerializedName("max_stacks")` -- the stackable state's cap became 1 and exactly one instance was
attached.

Measured this round: the loader's own comment describes that exact trap, and it guards against it -- but only for the RESOURCE
declaration (`requireKnownKeys(declared, RESOURCE_KEYS, ...)`). The `do` array, where every effect lives, is not walked.

The fix derives the allowed effect keys by REFLECTION from `EffectSpec`'s own `@SerializedName` annotations, so the list cannot
drift from what Gson actually maps -- which is precisely the property that was missing.

Readers: every content file (the whole corpus), and the specific sentence this was found on -- 1513's reward, whose cap key was
the one that vanished.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/data/TriggerTables.java"
text = io.open(PATH, encoding="utf-8").read()


def patch(old, new, label, count=1):
    global text
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    text = text.replace(old, new, count)
    print("ok   %s" % label)


patch(
    """        specs = GSON.fromJson(rules, SPEC_LIST);""",
    """        // ⭐ The same guard, one level deeper (2026-10-02). The comment on the resource check describes this exact trap --
        // \"Gson drops a key it does not know\" -- but only the resource declaration was walked, and an effect writing
        // `maxStacks` (the Java name) was accepted here and then dropped, leaving a stackable state with a cap of 1. The allowed
        // set comes from `EffectSpec`'s own `@SerializedName` annotations, so it cannot drift from what Gson maps.
        requireKnownEffectKeys(rules);
        specs = GSON.fromJson(rules, SPEC_LIST);""",
    "the loader walks the effects",
)

patch(
    """    private static void requireKnownKeys(JsonElement value, Set<String> known, String what) {""",
    """    /**
     * ⭐ The keys {@link EffectSpec} actually maps, read from its own annotations (2026-10-02).
     *
     * <p>Reflection rather than a hand-kept list: the failure this guards is exactly a key the loader \"knows\" and Gson does
     * not, so the two must be the same source of truth.
     */
    private static final Set<String> EFFECT_KEYS = effectKeys();

    private static Set<String> effectKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : com.laosun.aluminium.beans.EffectSpec.class.getDeclaredFields()) {
            com.google.gson.annotations.SerializedName name =
                    field.getAnnotation(com.google.gson.annotations.SerializedName.class);
            if (name != null) {
                keys.add(name.value());
            }
        }
        // `when`/`on`/`id` style keys never appear on an effect, and an empty set would reject every file: a reflection
        // result that came back empty is a broken build, not a validation result.
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "no @SerializedName fields were found on EffectSpec, so the effect keys cannot be checked");
        }
        return Set.copyOf(keys);
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

    private static void requireKnownKeys(JsonElement value, Set<String> known, String what) {""",
    "the reflection-derived key set and the walk",
)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("done")
