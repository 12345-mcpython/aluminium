package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

/**
 * One resource a character <b>declares</b>, from the {@code resources} block of
 * {@code resources/characters/<cid>.json} (P8-8, item 4 of the capability backlog):
 *
 * <pre>
 * {
 *   "resources": [
 *     { "id": "充能", "max": 3, "source": "1003 姬子 天赋 乘胜追击: "…上限3点"" }
 *   ],
 *   "rules": [ ... ]
 * }
 * </pre>
 *
 * <p><b>Why a declaration is needed at all.</b> {@link com.laosun.aluminium.models.ResourceManager#gain}
 * answers {@code 0} for an id it has never seen, and {@code value} answers {@code 0} too - so without a
 * declaration a {@code GAIN_RESOURCE} rule would load, fire, and quietly do nothing, and a
 * {@code self_resource:充能 >= 3} condition would be read as "0, so no". Both are wrong answers with no
 * symptom, which is the one failure mode this project refuses. The declaration is where "上限3点" - the
 * number a character's text actually states - is written down.
 *
 * <p><b>What it is not.</b> It is not a value: the <b>runtime</b> value lives on the combatant
 * ({@code CanHit.getResources()}), starts at {@link #initial} and is per battle, exactly like
 * {@code currentEnergy}. This record is configuration, read once when the character is built.
 *
 * <p>Note: A resource is registered by the character that owns it ({@link
 * com.laosun.aluminium.enums.ResourceScope#SELF}); a party-level resource is refused by
 * {@code ResourceManager.register} and is therefore not expressible here either.
 *
 * <p>Validated in the compact constructor, so <b>both</b> paths - Gson reading the JSON and a test or
 * another loader building one by hand - refuse a half-valid declaration. (Gson constructs records through
 * their canonical constructor, which is the compact one; {@code CharacterResourceTest} pins that for the JSON
 * path specifically, since the alternative would be a declaration that silently validated nothing.)
 *
 * @param id      the identifier, as the rules spell it in {@code "resource"} and in
 *                {@code self_resource:<NAME>} (e.g. {@code 充能})
 * @param max     the normal cap: "上限3点" is {@code 3}
 * @param initial the value the resource starts a battle with ({@code 0} when the JSON omits it)
 * @param source  where the cap comes from - the document, the ability and the sentence, with the parameter
 *                list, so the number can be traced back. Not read by the engine; the project's rule is that
 *                every content number states its origin (same field as {@code TriggerSpec.source})
 * @param note    free-form note for the next reader (may be absent)
 */
public record ResourceSpec(@SerializedName("id") String id,
                           @SerializedName("max") Integer max,
                           @SerializedName("initial") Integer initial,
        // A declared OVERFLOW (2026-09-30; reader: 1506's [隐藏分]: "达到 60 点后可激活终结技，\n // 达到上限后还可溢出 240 点"). `Resource` has had both tiers all along (`max` + `maxOverflow`);
        // what was missing was a way for a DECLARATION to state the second number.
        @SerializedName("overflow") Integer overflow,
        // A declared SCOPE (2026-09-30; reader: the shared 笑点 counter, "获得 10 点笑点"). Note: A party-level resource needs a
        // PER-BATTLE owner rather than a copy per character -- which is exactly what `ResourceManager.register` says when it
        // refuses an unwired scope, and what `Battle` now provides.
        @SerializedName("scope") String scope,
                           @SerializedName("source") String source,
                           @SerializedName("note") String note) {

    /**
     * @throws IllegalArgumentException when the id is blank or contains whitespace, when {@code max} is
     *                                  absent or below 1, or when {@code initial} is outside
     *                                  {@code [0, max]}
     */
    public ResourceSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "A resource declaration needs an \"id\" (the name the rules spell in \"resource\"); "
                            + "it is written as e.g. { \"id\": \"充能\", \"max\": 3 }");
        }
        id = id.trim();
        // Whitespace is refused rather than trimmed away silently: the same name is spelled inline inside a
        // condition (`self_resource:充能 >= 3`), where the DSL splits on the operator and trims -- a name
        // with a space inside would work in `resource` and read as a different, never-declared name in the
        // condition, i.e. a rule that loads and then fails at assembly for a reason nobody can see.
        if (id.chars().anyMatch(c -> java.lang.Character.isWhitespace(c))) {
            throw new IllegalArgumentException(
                    "Resource id contains whitespace; a resource name is also written inline in conditions "
                            + "(\"self_resource:<NAME> >= 3\"), where a space cannot be told from the spacing "
                            + "around the operator");
        }
        if (max == null) {
            throw new IllegalArgumentException(
                    "Resource \"" + id + "\" declares no \"max\". The cap is required: Resource's own "
                            + "default is 0, so every gain would be a no-op and the rules would read as "
                            + "\"this resource is always empty\" with nothing to report");
        }
        if (max < 1) {
            throw new IllegalArgumentException(
                    "Resource \"" + id + "\" declares \"max\": " + max + "; a resource that cannot hold "
                            + "anything is a mistake, not a way to spell \"unused\" (drop the declaration)");
        }
        int start = initial == null ? 0 : initial;
        if (start < 0 || start > max) {
            throw new IllegalArgumentException(
                    "Resource \"" + id + "\" declares \"initial\": " + start + ", which is outside [0, "
                            + max + "]. Resource itself clamps, and a silently clamped start is a number "
                            + "nobody can see");
        }
        initial = start;
    }

    /**
     * A declaration that starts at 0 and states no provenance - the ordinary case in a test.
     */
    public ResourceSpec(String id, int max) {
        this(id, max, 0, null, null, null, null);
    }

    /**
     * The same, starting at a stated value.
     */
    public ResourceSpec(String id, int max, int initial) {
        this(id, max, initial, null, null, null, null);
    }
}
