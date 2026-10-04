"""Fallen items, batch 1 (round 1694).

Measured this round, and it corrects an earlier claim of mine:
  * `TriggerSpec.onAny` IS spelled `@SerializedName("on_any")` and `TriggerTable` does read it (L131) -- my round-1689 failure was
    a rule with NO `on` at all. `on_any` ADDS events; `on` stays required. So there is no "field that cannot be written" hole;
    the record gets corrected instead of a capability being built.
  * The real hole: RULE OBJECTS have no key guard. Only the top-level `{resources, rules}` keys and the `do` array's effects are
    checked, so a mistyped rule key (`whenn`, `onn`) is still dropped by Gson without a word -- and a rule with neither `on` nor
    `on_any` reaches the engine as "Unknown trigger event 'null'" instead of failing at load.

So this script: rule-object key guard + "a rule must state on or on_any", the other half of 1408's seed trace, and the two
readings that go with them (the battle-start seed, and 「最多叠加 2 层」).
"""
import io
import json
import os
import sys

TABLES = "src/main/java/com/laosun/aluminium/data/TriggerTables.java"
CHAR = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationEndClausesTest.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


# ---- 1. the rule-object guard, mirroring the effect one
patch(
    TABLES,
    """    /** Walks every rule's {@code do} array and refuses a key Gson would silently drop. */""",
    """    /**
     * \u2b50 The keys {@link com.laosun.aluminium.beans.TriggerSpec} actually maps, read from its own annotations (2026-10-02).
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

    /** \u2b50 Walks the rules themselves: known keys, and an event to listen to (2026-10-02). */
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
                        "a rule states neither \\"on\\" nor \\"on_any\\", so it would never fire (its keys: "
                                + object.keySet().stream().sorted().toList() + ")");
            }
        }
    }

    /** Walks every rule's {@code do} array and refuses a key Gson would silently drop. */""",
    "the rule-object guard",
)

patch(
    TABLES,
    """        requireKnownEffectKeys(rules);""",
    """        requireKnownRuleKeys(rules);
        requireKnownEffectKeys(rules);""",
    "the object form guards its rules",
)

patch(
    TABLES,
    """        if (root.isJsonArray()) {
            specs = GSON.fromJson(root, SPEC_LIST);""",
    """        if (root.isJsonArray()) {
            requireKnownRuleKeys(root);
            requireKnownEffectKeys(root);
            specs = GSON.fromJson(root, SPEC_LIST);""",
    "the bare-array form guards its rules too",
)

# ---- 2. the other half of the seed trace
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
if any(isinstance(rule, dict) and rule.get("id") == "trace_worlds_end_one_seed_at_battle_start" for rule in rules):
    sys.exit("REFUSING: the battle-start half is already there")
rules.append({
    "on": "BATTLE_START",
    "id": "trace_worlds_end_one_seed_at_battle_start",
    "do": [{"op": "GAIN_RESOURCE", "resource": "\u706b\u79cd", "amount": 1, "target": "self"}],
    "source": "1408 \u767d\u5384 \u884c\u8ff9 \u884c\u5411\u4e16\u754c\u7ec8\u70b9 (1408101)\uff1a\u300c**\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011**\u3002\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u83b7\u5f97 3 \u70b9\u3010\u706b\u79cd\u3011\u300d",
    "note": "\u300c**\u6218\u6597\u5f00\u59cb\u65f6**\uff0c\u83b7\u5f97 **1** \u70b9\u3010\u706b\u79cd\u3011\u300d\u21d2 `BATTLE_START` \u21d2 `GAIN_RESOURCE{\u706b\u79cd, 1}` \u2713\u3002"
            "\u26a0 \u4e0e\u540c\u53e5\u7684\u53e6\u4e00\u534a\uff08\u53d8\u8eab\u7ed3\u675f +3 \u2713\uff09\u662f**\u4e24\u6761\u89c4\u5219** \u2713 \u2014\u2014 "
            "\u4e24\u4e2a\u4e8b\u4ef6\u3001\u4e24\u5957\u6761\u4ef6\uff08\u4e00\u4e2a\u65e0\u6761\u4ef6\u3001\u4e00\u4e2a `self state_ended \u53d8\u8eab` \u2713\uff09\u3002",
})
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1408's seed trace now has both halves (%d rules)" % len(rules))

# ---- 3. the two readings
text = io.open(JUDGE, encoding="utf-8").read()
OLD = """        int before = owner.getResources().value(SEEDS);"""
NEW = """        int before = owner.getResources().value(SEEDS);
        // \u2b50 \u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d -- the other half of the same trace line (1408101).
        Assertions.assertEquals(1, before, "\u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the seed anchor appears %d times" % text.count(OLD))
text = text.replace(OLD, NEW, 1)

CAP_TEST = """
    /**
     * \u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u653b\u51fb\u529b\u63d0\u9ad8 50%\u3002\u8be5\u6548\u679c**\u6700\u591a\u53e0\u52a0 2 \u5c42**\u300d-- read by firing the end clause MORE times than the cap allows.
     *
     * <p>\u26a0 The state is applied directly for the second and third ends: the transformation is only granted by the ultimate, and
     * what this reading is about is the CAP, not how the state got there. The seed clause beside it is uncapped, so its growth
     * proves the later firings really happened -- without that, a flat ATK could just mean "nothing fired".
     */
    @Test
    public void theEndClauseCapsAtTwoLayers() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // one end, from a state applied directly
        owner.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterOneEnd = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterOneEnd = owner.getResources().value(SEEDS);

        // and a second end: layer three, which the cap refuses
        owner.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterTwoEnds = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterTwoEnds = owner.getResources().value(SEEDS);
        System.out.println("[end-clauses] atk " + atkAfterOneEnd + " -> " + atkAfterTwoEnds
                + " ; seeds " + seedsAfterOneEnd + " -> " + seedsAfterTwoEnds);

        Assertions.assertEquals(seedsAfterOneEnd + 3, seedsAfterTwoEnds,
                "the second end really fired -- the seed clause has no cap");
        Assertions.assertEquals(0.0, atkAfterTwoEnds - atkAfterOneEnd, 1e-9,
                "\u300c\u6700\u591a\u53e0\u52a0 2 \u5c42\u300d: with the battle-start layer that is already two, so the third is dropped");
    }
}
"""
text = text.rstrip()
if not text.endswith("}"):
    sys.exit("REFUSING: unexpected judge tail")
text = text[:-1].rstrip() + "\n" + CAP_TEST
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge reads both halves and the cap")

# ---- 4. does any shipped content use on_any already?
users = []
for name in sorted(os.listdir("src/main/resources/characters")):
    if name.endswith(".json"):
        if '"on_any"' in io.open("src/main/resources/characters/" + name, encoding="utf-8").read():
            users.append(name)
print("content files using on_any: %s" % (users or "(none)"))
