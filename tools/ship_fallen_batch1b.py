"""Finish batch 1 (round 1694): the bare-array branch, the ragged indentation, the content, the readings."""
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


# ---- 1. the bare-array branch (16/20 spaces, measured with the read tool)
patch(
    TABLES,
    """                if (root.isJsonArray()) {
                    specs = GSON.fromJson(root, SPEC_LIST);""",
    """                if (root.isJsonArray()) {
                    requireKnownRuleKeys(root);
                    requireKnownEffectKeys(root);
                    specs = GSON.fromJson(root, SPEC_LIST);""",
    "the bare-array form guards its rules and effects",
)

# ---- 2. tidy the indentation my earlier patch left at 8 spaces
patch(
    TABLES,
    """        // "Gson drops a key it does not know" -- but only the resource declaration was walked, and an effect writing
        // `maxStacks` (the Java name) was accepted here and then dropped, leaving a stackable state with a cap of 1. The allowed
        // set comes from `EffectSpec`'s own `@SerializedName` annotations, so it cannot drift from what Gson maps.
        requireKnownRuleKeys(rules);
        requireKnownEffectKeys(rules);""",
    """                    // "Gson drops a key it does not know" -- but only the resource declaration was walked, and an effect
                    // writing `maxStacks` (the Java name) was accepted and then dropped, leaving a stackable state with a cap
                    // of 1. The allowed sets come from `EffectSpec`'s and `TriggerSpec`'s own `@SerializedName` annotations,
                    // so they cannot drift from what Gson maps.
                    requireKnownRuleKeys(rules);
                    requireKnownEffectKeys(rules);""",
    "the inserted block is indented like its neighbours",
)

# ---- 3. the other half of the seed trace
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]
if not any(isinstance(rule, dict) and rule.get("id") == "trace_worlds_end_one_seed_at_battle_start" for rule in rules):
    rules.append({
        "on": "BATTLE_START",
        "id": "trace_worlds_end_one_seed_at_battle_start",
        "do": [{"op": "GAIN_RESOURCE", "resource": "\u706b\u79cd", "amount": 1, "target": "self"}],
        "source": "1408 \u767d\u5384 \u884c\u8ff9 \u884c\u5411\u4e16\u754c\u7ec8\u70b9 (1408101)\uff1a\u300c**\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011**\u3002\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u83b7\u5f97 3 \u70b9\u3010\u706b\u79cd\u3011\u300d",
        "note": "\u300c**\u6218\u6597\u5f00\u59cb\u65f6**\uff0c\u83b7\u5f97 **1** \u70b9\u3010\u706b\u79cd\u3011\u300d\u21d2 `BATTLE_START` \u21d2 `GAIN_RESOURCE{\u706b\u79cd, 1}` \u2713\u3002"
                "\u26a0 \u4e0e\u540c\u53e5\u7684\u53e6\u4e00\u534a\uff08\u53d8\u8eab\u7ed3\u675f +3 \u2713\uff09\u662f**\u4e24\u6761\u89c4\u5219** \u2713\uff1a"
                "\u4e24\u4e2a\u4e8b\u4ef6\u3001\u4e24\u5957\u6761\u4ef6\uff08\u4e00\u4e2a\u65e0\u6761\u4ef6 \u2713\u3001\u4e00\u4e2a `self state_ended \u53d8\u8eab` \u2713\uff09"
                "\u2014\u2014 \u26a0 \u5b9e\u6d4b\uff1a`on_any` **\u662f**\u53ef\u7528\u7684\uff08`@SerializedName(\"on_any\")` \u2713\u3001`TriggerTable` \u4f1a\u8bfb \u2713\uff09\uff0c"
                "\u4f46\u5b83\u53ea\u80fd**\u8ffd\u52a0\u4e8b\u4ef6**\u3001**\u5171\u7528\u540c\u4e00\u5957\u6761\u4ef6** \u2717 \u21d2 \u672c\u53e5\u7528\u4e0d\u4e0a \u2713\u3002",
    })
    with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("ok   1408's seed trace now has both halves (%d rules)" % len(rules))
else:
    print("ok   the battle-start half was already there")

# ---- 4. the readings
text = io.open(JUDGE, encoding="utf-8").read()
OLD = "        int before = owner.getResources().value(SEEDS);"
NEW = ("        int before = owner.getResources().value(SEEDS);\n"
       "        // \u2b50 \u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d -- the other half of the same trace line (1408101).\n"
       "        Assertions.assertEquals(1, before, \"\u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d\");")
if "the other half of the same trace line" not in text:
    if text.count(OLD) != 1:
        sys.exit("REFUSING: the seed anchor appears %d times" % text.count(OLD))
    text = text.replace(OLD, NEW, 1)

CAP_TEST = """
    /**
     * \u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\uff0c\u653b\u51fb\u529b\u63d0\u9ad8 50%\u3002\u8be5\u6548\u679c**\u6700\u591a\u53e0\u52a0 2 \u5c42**\u300d-- read by firing the end clause more times than the cap allows.
     *
     * <p>\u26a0 The state is applied directly for these ends: the transformation is only granted by the ultimate, and what this reading
     * is about is the CAP. The seed clause beside it has no cap, so its growth proves the later firings really happened --
     * without that, a flat ATK could just mean "nothing fired".
     */
    @Test
    public void theEndClauseCapsAtTwoLayers() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        owner.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterOneEnd = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterOneEnd = owner.getResources().value(SEEDS);

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
if "theEndClauseCapsAtTwoLayers" not in text:
    text = text.rstrip()
    if not text.endswith("}"):
        sys.exit("REFUSING: unexpected judge tail")
    text = text[:-1].rstrip() + "\n" + CAP_TEST
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge reads both halves and the cap")

users = [name for name in sorted(os.listdir("src/main/resources/characters"))
         if name.endswith(".json")
         and '"on_any"' in io.open("src/main/resources/characters/" + name, encoding="utf-8").read()]
print("content files using on_any: %s" % (users or "(none)"))
