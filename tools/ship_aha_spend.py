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
        // \u2b50 A PARTY counter has no home on the unit (the same fact `gainResource` records), so a spend has to fall back to the
        // battle-level one -- measured: \u3010\u7b11\u70b9\u3011 is declared `scope: PARTY` (\u300c\u7b11\u70b9\u4e3a\u5168\u961f\u5171\u4eab\u300d, glossary 10000027) and the holder's
        // own Resources does not have it, so \u300c\u963f\u54c8\u884c\u52a8\u540e\u4f1a\u6d88\u8017\u5168\u90e8\u7b11\u70b9\u300d (10000026) could not be written at all.
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
rule["do"].append({"op": "SPEND_RESOURCE", "resource": "\u7b11\u70b9", "spendAll": True, "target": "self"})
rule["note"] = rule["note"].replace(
    " \u26a0 \u540c\u53e5\u300c\u963f\u54c8\u884c\u52a8\u540e\u4f1a\u6d88\u8017\u5168\u90e8\u7b11\u70b9\u300d\uff08\u6d88\u8017 \u2717\uff09\u4ecd\u672a\u5199 \u2713\u3002",
    "") + (
    " \u2b50 **\u540c\u53e5\u7684\u53e6\u4e00\u534a\u4e5f\u5df2\u5199\uff08\u672c\u8f6e \u2713\uff09**\uff1a\u300c**\u963f\u54c8\u884c\u52a8\u540e\u4f1a\u6d88\u8017\u5168\u90e8\u7b11\u70b9**\u300d\u21d2 "
    "**\u540c\u4e00\u6761\u89c4\u5219\u7684\u7b2c\u4e8c\u4e2a\u6548\u679c** `SPEND_RESOURCE{\u7b11\u70b9, spendAll: true}` \u2713\u3002"
    "\u26a0 **\u4e3a\u4f55\u653e\u5728\u540c\u4e00\u6761\u89c4\u5219\u91cc** \u2713\uff1a\u793c\u7269\u5fc5\u987b**\u5148**\u8bfb\u5230\u7b11\u70b9\u6570 \u2717\uff08\u5426\u5219\u5b83\u53ea\u80fd\u5f97\u5230 0 \u5c42 \u2717\uff09\uff0c"
    "\u800c\u5f15\u64ce\u4fdd\u8bc1\u7684\u53ea\u6709**\u540c\u4e00\u6761\u89c4\u5219\u5185**\u7684\u6548\u679c\u987a\u5e8f \u2713\uff08\u5148\u4f8b\uff1a`1314` \u7684\u6ce8\u8bb0\u91cc\u201c\u653e\u5728\u52a0\u5c42\u4e4b\u540e\u201d \u2713\uff09\uff1b"
    "\u800c\u8bed\u6599\u81ea\u5df1\u7684\u8bed\u5e8f\u4e5f\u662f**\u5148\u53d1\u72b6\u6001\u3001\u540e\u6d88\u8017** \u2713\u3002"
    "\u26a0 \u5f15\u64ce\u4fa7\u540c\u65f6\u8865\u4e86**\u961f\u7ea7\u8d44\u6e90\u7684\u6d88\u8017** \u2713\uff08`SPEND_RESOURCE` \u539f\u672c\u53ea\u770b\u5355\u4f4d\u81ea\u5df1 \u2717 \u21d2 \u5bf9 `scope: PARTY` \u4f1a\u62a5\u9519 \u2717\uff09\u3002")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1513's reward now grants the state and then spends the laughs")

# ---- 3. the reading: both halves in the same scene
text = io.open(JUDGE, encoding="utf-8").read()
OLD = '        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");'
NEW = ('        Assertions.assertEquals(4, instances, "the state carries one instance per laugh");\n'
       '        // \u2b50 \u300c\u963f\u54c8\u884c\u52a8\u540e\u4f1a\u6d88\u8017\u5168\u90e8\u7b11\u70b9\u300d (glossary 10000026): the count was read FIRST, then the counter was emptied --\n'
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
