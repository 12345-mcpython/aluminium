"""Roll back the two content edits, and register what was measured (round 4 of the goal).

Kept: nothing unverified. Recorded: the slot identification (9/11 by toughness) and the two failures, each with its own reading,
because those are the facts the next attempt needs.
"""
import io
import json
import os
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
EXPRESSION = "EXPRESSION.md"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]

# ---- 1. the turn-start rule goes back to exactly what it was
rule = next(entry for entry in rules if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
rule["when"] = ["actor == self"]
rule["do"] = [{"op": "CAST_SKILL", "skill": "SKILL", "target": "target"}]
rule["note"] = ("⭐ 2026-09-30：用 `CAST_SKILL` 让那个单位**立即施放一次** `SKILL` ✓ —— ⚠ 读的是**它自己的数据行** ✓"
                 "（而不是手写倍率 ✗），⚠ 而护栏会拒绝**不造成伤害**的技能 ✗。"
                 "⚠ 来源：`CAST_SKILL` 是 `commandSummon` 放宽三处而来（见 `aggro 回收之七百八十五`）；⚠ 它不经过战技点消耗 ✓。"
                 "⚠⚠ **2026-10-02 实测两件（本轮试写后回滚 ✓）**："
                 "① 句子是「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」✓，"
                 "而本条**没有 `self has_state 血仇` 门** ✗、也**没有先把槽位换成强化战技** ✗"
                 "（槽 **9** ✓，见 `SkillData.init(1404, 9)` 破韧 **60/30** ✓ 与语料逐字相符 ✓）"
                 "⇒ **声称与执行不一致** ✗；② 本轮把它改成“先换再放”并加上门 ✓ 后，"
                 "判据读到：敌人**挨了打** ✓（16498.30 → 15730.71 ✓）但他**自己的血没掉** ✗。"
                 "⚠ 两种可能尚未分开 ✗：（a）换装没生效 ✗（仍在放槽 2 的普通战技 ✓，它也会造伤 ✓）；"
                 "（b）换装生效了 ✓ 但数据行里「消耗等同于万敌**当前生命值 35%** 的生命值」这一列**没有被执行** ✗。"
                 "⭐ 分辨方法（下一步 ✓）：直接量 `him.getSkills().get(SKILL).getSkillSlot()` —— "
                 "原始行是 **2** ✓、换入后是 **9** ✓（`DefaultSkill.getSkillSlot()` 返回的就是它被构造时的槽 ✓）。")
rule["source"] = None
rule.pop("source", None)

# ---- 2. the 150-charge rule comes out
kept = [entry for entry in rules
        if not (isinstance(entry, dict) and entry.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty")]
if len(kept) != len(rules) - 1:
    sys.exit("REFUSING: the 150-charge rule was not found")
doc["rules"] = kept
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404 is back to what it was (%d rules)" % len(kept))

if os.path.exists(JUDGE):
    os.remove(JUDGE)
    print("ok   the unverified judge is gone")

# ---- 3. the §3 row now carries the two failures and the discriminator
lines = io.open(EXPRESSION, encoding="utf-8").read().split("\n")
PREFIX = "| **「充能达到 150 点时"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「充能达到 150 点时，立即获得 1 个额外回合并自动施放【弑神登神】」**（万敌的**强化战技** ✓） "
    "| ⭐ 实测已把它缩到**两件** ✓：① **强化战技的自身代价没有被执行** ✗"
    "（判据：敌人挨打 16498.30→15730.71 ✓ 而他自己的血不动 ✗ —— ⚠ 尚未分开“换装没生效”与“该列没执行” ✗）；"
    "② 「`resource_changed:<名>`」**需要由 op 抬起事件** ✗（判据：直接 `fireTriggers(RESOURCE_CHANGED, …)` 时 充能 150→150、敌人毫发无损 ✗；"
    "☠ `Cone20024Test` 的注记说的就是这件：“**the op's own change** fired RESOURCE_CHANGED — that is the wiring” ✓） "
    "| `1404`（ 1 位）、**`1415` 的忆灵技能 8** ✓ **共 2 位** ✓ "
    "| ⭐ **槽位已指名** ✓：【弑王成王】=槽 **9** ✓、【弑神登神】=槽 **11** ✓"
    "（由 `SkillData.init(1404, 槽).stanceFor(…)` 的破韧 **60/30** 与 **90/60** 对上语料 ✓，标定用 `1301` 的槽 8 ✓）；"
    "下一步：先用 `getSkillSlot()` 把“换装是否生效”分开 ✓ |")
io.open(EXPRESSION, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row carries both failures and the named slots")
