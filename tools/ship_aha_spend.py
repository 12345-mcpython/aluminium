"""Part 1a: 「阿哈行动后会消耗全部笑点」 (round 1693).

Measured before writing:
  * the authoritative sentence is the official glossary's entry 10000026 阿哈: 「阿哈时刻持续至本次最后一个欢愉技施放结束。阿哈时刻结束时，
    使参演的角色获得本次计入笑点的【好活当赏】状态，持续 2 回合。**阿哈行动后会消耗全部笑点**。」 -- and 10000027 笑点 says
    「**笑点为全队共享**」, which is the measured justification for `scope: PARTY` in both files;
  * the reward must read the count BEFORE the spend, and the corpus's own order puts the reward first; within ONE rule the
    engine guarantees effect order (the project's own precedent: 1314's note says the stacked modifier is placed after the
    stack grant so it "读到的层数包含刚加的这些");
  * `SPEND_RESOURCE` only ever touched `holder.getResources()`, and a PARTY counter 「has no home on the unit」 (the same
    comment `gainResource` carries) -- so it would have thrown. The engine gains the mirror of `gainResource`'s fallback.

So: engine fallback + one second effect on 1513's reward + the reading that proves both halves at once.
"""
import io
import json
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
CHAR = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/GiftCarriesTheLaughsTest.java"

# ---- 1. the engine: a PARTY counter can be spent, exactly as it can be gained
text = io.open(INTERP, encoding="utf-8").read()
ANCHOR = """        CanHit holder = resolveTarget(effect, ctx);
        String id = effect.getResource();"""
if text.count(ANCHOR) != 1:
    sys.exit("REFUSING: the SPEND_RESOURCE head appears %d times" % text.count(ANCHOR))
FALLBACK = ANCHOR + """
        // ⭐ A PARTY counter has no home on the unit (the same fact `gainResource` records), so a spend has to fall back to the
        // battle-level one -- measured: 【笑点】 is declared `scope: PARTY` (「笑点为全队共享」, glossary 10000027) and the holder's
        // own Resources does not have it, so 「阿哈行动后会消耗全部笑点」 (10000026) could not be written at all.
        com.laosun.aluminium.models.Resource partyCounter = holder.getResources().has(id)
                ? null
                : (ctx.battle() == null ? null : ctx.battle().partyResource(id));
        if (partyCounter != null) {
            int spend = Boolean.TRUE.equals(effect.getSpendAll())
                    ? partyCounter.value()
                    : (int) Math.round(scaledAmount(effect, ctx));
            if (!partyCounter.spendExactly(spend)) {
                throw new IllegalStateException(
                        "SPEND_RESOURCE '" + id + "' needs " + spend + " but the party has only "
                                + partyCounter.value());
            }
            return;
        }"""
io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text.replace(ANCHOR, FALLBACK, 1))
print("ok   SPEND_RESOURCE falls back to the battle-level PARTY counter")

# ---- 2. the content: the spend rides on the same rule, after the grant
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "elation_moment_reward")
if any(effect.get("op") == "SPEND_RESOURCE" for effect in rule["do"]):
    sys.exit("REFUSING: the spend is already there")
if rule["do"][0].get("op") != "APPLY_BUFF":
    sys.exit("REFUSING: the grant is not the first effect any more")
rule["do"].append({"op": "SPEND_RESOURCE", "resource": "笑点", "spendAll": True, "target": "self"})
rule["note"] = rule["note"].replace(
    " ⚠ 同句「阿哈行动后会消耗全部笑点」（消耗 ✗）仍未写 ✓。",
    "") + (
    " ⭐ **同句的另一半也已写（本轮 ✓）**：「**阿哈行动后会消耗全部笑点**」⇒ "
    "**同一条规则的第二个效果** `SPEND_RESOURCE{笑点, spendAll: true}` ✓。"
    "⚠ **为何放在同一条规则里** ✓：礼物必须**先**读到笑点数 ✗（否则它只能得到 0 层 ✗），"
    "而引擎保证的只有**同一条规则内**的效果顺序 ✓（先例：`1314` 的注记里“放在加层之后” ✓）；"
    "而语料自己的语序也是**先发状态、后消耗** ✓。"
    "⚠ 引擎侧同时补了**队级资源的消耗** ✓（`SPEND_RESOURCE` 原本只看单位自己 ✗ ⇒ 对 `scope: PARTY` 会报错 ✗）。")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1513's reward now grants the state and then spends the laughs")

# ---- 3. the reading: both halves in the same scene
text = io.open(JUDGE, encoding="utf-8").read()
OLD = '        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");'
NEW = ('        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");\n'
       '        // ⭐ 「阿哈行动后会消耗全部笑点」 (glossary 10000026): the count was read FIRST, then the counter was emptied --\n'
       '        // asserting both in one scene is what makes the ordering part of the reading rather than of the prose.\n'
       '        Assertions.assertEquals(0, battle.partyResourceValue(LAUGHS),\n'
       '                "and all the laughs are spent at the same moment, after the gift has taken its count");')
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge now reads both the count and the spending")

# ---- 4. what 1408 declares, for the next step's test
with io.open("src/main/resources/characters/1408.json", encoding="utf-8") as handle:
    doc1408 = json.load(handle)
for entry in doc1408.get("resources") or []:
    print("   1408 resource: %s scope=%r max=%s" % (entry.get("id"), entry.get("scope"), entry.get("max")))
