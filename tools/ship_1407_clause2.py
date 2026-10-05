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
names = sorted(set(re.findall(r"获得【([^】]+)】状态", corpus)))
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
    "source": ("1407 遐蝶 仓库技 「月茉之庇」：「若我方角色受到致命攻击，则**本次行动中所有受到致命"
               "攻击的我方角色**获得【" + state + "】状态。【" + state + "】状态下的角色会**暂时延后陷入无法战斗状态**」。"
               "（选择器 `all_allies_lethally_hit_this_action` 读的是 `Battle.lethallyHitThisAction()`；`defers_death: true` 让该状态成为"
               "`DeferredDeathBuff`，即「延后」本身。）"),
    "note": ("⚠ 本条**不含**原句的另两半：「若行动后、下一次回合开始前生命值提高或获得护盾，则解除」"
             "（需要一个关于**别的单位**被治疗/获盾的事件，而仓库规则只能写在**持有者自己的表**上）"
             "与「单场战斗中最多触发 1 次」（需要一个对**整条子句**的战斗级上限）。"),
}]
io.open("src/main/resources/warehouse/1407.json", "w", encoding="utf-8", newline="\n").write(
    json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   warehouse/1407.json written (bare array, sibling's gating, corpus's state name)")
