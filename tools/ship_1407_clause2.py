"""Write warehouse/1407.json in the shape its shipped sibling uses (2026-10-02).

Measured: `warehouse/1506.json` is a BARE ARRAY of rules, not an object with a cid/name header, and its gating lives in the rule's own
`when`. Since both files are warehouse clauses (the same `TriggerTables.warehouse` family), the gating is copied from the sibling VERBATIM
rather than invented -- guessing a condition name is exactly the "silent lie" this project refuses.

The clause, verbatim from aluminium_texts/1407_遐蝶.md:
  「战斗中，若我方角色受到致命攻击，则本次行动中所有受到致命攻击的我方角色获得【月茧】状态。【月茧】状态下的角色会暂时延后陷入无法战斗
   状态，且可以正常行动。若行动后，下一次回合开始前当前生命值提高或获得护盾，则解除【月茧】状态，否则将立即陷入无法战斗状态。
   该效果单场战斗中最多触发 1 次。」
"""
import glob
import io
import json
import re
import sys

SIBLING = "src/main/resources/warehouse/1506.json"
sibling = json.load(io.open(SIBLING, encoding="utf-8"))
if not isinstance(sibling, list):
    sys.exit("REFUSING: the sibling is not a bare array any more")
print("sibling rules: %d" % len(sibling))
for r in sibling:
    print("  id=%s on=%s when=%s do=%s" % (r.get("id"), r.get("on"),
          json.dumps(r.get("when"), ensure_ascii=False), json.dumps(r.get("do"), ensure_ascii=False)[:160]))
gating = sibling[0].get("when")
if gating is None:
    sys.exit("REFUSING: the sibling states no gating to reuse")

texts = glob.glob(r"E:\turnbasedgamedata\aluminium_texts\1407_*.md")
if not texts:
    sys.exit("REFUSING: 1407's text is missing")
corpus = io.open(texts[0], encoding="utf-8", errors="replace").read()
names = sorted(set(re.findall(r"\u83b7\u5f97\u3010([^\u3011]+)\u3011\u72b6\u6001", corpus)))
print("state names in the corpus: %s" % names)
if len(names) != 1:
    sys.exit("REFUSING: ambiguous state name")
state = names[0]

doc = [{
    "id": "mooncocoon_holds_every_lethal_blow_of_one_action",
    "on": "LETHAL_DAMAGE",
    "when": gating,
    "do": [{
        "op": "APPLY_BUFF",
        "buff": state,
        "turns": 1,
        "defers_death": True,
        "target": "all_allies_lethally_hit_this_action",
    }],
    "source": ("1407 \u9050\u8776 \u4ed3\u5e93\u6280 \u300c\u6708\u8309\u4e4b\u5e87\u300d\uff1a\u300c\u82e5\u6211\u65b9\u89d2\u8272\u53d7\u5230\u81f4\u547d\u653b\u51fb\uff0c\u5219**\u672c\u6b21\u884c\u52a8\u4e2d\u6240\u6709\u53d7\u5230\u81f4\u547d"
               "\u653b\u51fb\u7684\u6211\u65b9\u89d2\u8272**\u83b7\u5f97\u3010" + state + "\u3011\u72b6\u6001\u3002\u3010" + state + "\u3011\u72b6\u6001\u4e0b\u7684\u89d2\u8272\u4f1a**\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001**\u300d\u3002"
               "\uff08\u9009\u62e9\u5668 `all_allies_lethally_hit_this_action` \u8bfb\u7684\u662f `Battle.lethallyHitThisAction()`\uff1b`defers_death: true` \u8ba9\u8be5\u72b6\u6001\u6210\u4e3a"
               "`DeferredDeathBuff`\uff0c\u5373\u300c\u5ef6\u540e\u300d\u672c\u8eab\u3002\uff09"),
    "note": ("\u26a0 \u672c\u6761**\u4e0d\u542b**\u539f\u53e5\u7684\u53e6\u4e24\u534a\uff1a\u300c\u82e5\u884c\u52a8\u540e\u3001\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u751f\u547d\u503c\u63d0\u9ad8\u6216\u83b7\u5f97\u62a4\u76fe\uff0c\u5219\u89e3\u9664\u300d"
             "\uff08\u9700\u8981\u4e00\u4e2a\u5173\u4e8e**\u522b\u7684\u5355\u4f4d**\u88ab\u6cbb\u7597/\u83b7\u76fe\u7684\u4e8b\u4ef6\uff0c\u800c\u4ed3\u5e93\u89c4\u5219\u53ea\u80fd\u5199\u5728**\u6301\u6709\u8005\u81ea\u5df1\u7684\u8868**\u4e0a\uff09"
             "\u4e0e\u300c\u5355\u573a\u6218\u6597\u4e2d\u6700\u591a\u89e6\u53d1 1 \u6b21\u300d\uff08\u9700\u8981\u4e00\u4e2a\u5bf9**\u6574\u6761\u5b50\u53e5**\u7684\u6218\u6597\u7ea7\u4e0a\u9650\uff09\u3002"),
}]
io.open("src/main/resources/warehouse/1407.json", "w", encoding="utf-8", newline="\n").write(
    json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   warehouse/1407.json written (bare array, sibling's gating, corpus's state name)")
