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
        "do": [{"op": "GAIN_RESOURCE", "resource": "火种", "amount": 1, "target": "self"}],
        "source": "1408 白厄 行迹 行向世界终点 (1408101)：「**战斗开始时，获得 1 点【火种】**。变身结束时，获得 3 点【火种】」",
        "note": "「**战斗开始时**，获得 **1** 点【火种】」⇒ `BATTLE_START` ⇒ `GAIN_RESOURCE{火种, 1}` ✓。"
                "⚠ 与同句的另一半（变身结束 +3 ✓）是**两条规则** ✓："
                "两个事件、两套条件（一个无条件 ✓、一个 `self state_ended 变身` ✓）"
                "—— ⚠ 实测：`on_any` **是**可用的（`@SerializedName(\"on_any\")` ✓、`TriggerTable` 会读 ✓），"
                "但它只能**追加事件**、**共用同一套条件** ✗ ⇒ 本句用不上 ✓。",
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
       "        // ⭐ 「战斗开始时，获得 1 点【火种】」 -- the other half of the same trace line (1408101).\n"
       "        Assertions.assertEquals(1, before, \"「战斗开始时，获得 1 点【火种】」\");")
if "the other half of the same trace line" not in text:
    if text.count(OLD) != 1:
        sys.exit("REFUSING: the seed anchor appears %d times" % text.count(OLD))
    text = text.replace(OLD, NEW, 1)

CAP_TEST = """
    /**
     * 「进入战斗或变身结束时，攻击力提高 50%。该效果**最多叠加 2 层**」-- read by firing the end clause more times than the cap allows.
     *
     * <p>⚠ The state is applied directly for these ends: the transformation is only granted by the ultimate, and what this reading
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
                "「最多叠加 2 层」: with the battle-start layer that is already two, so the third is dropped");
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
