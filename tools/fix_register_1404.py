"""Correct the 1408 row, and the stale half of 1404's register (round 3 of the goal).

Measured this round:
  * 【弑神登神】 is 万敌's ENHANCED skill (「强化战技：弑神登神 / Godslayer Be God」), not 1408's -- her page never mentions it, and the
    data tree only carries it on 1404's and 1415's pages;
  * 「自动施放【X】」 ALREADY has a shipped spelling: `CAST_SKILL` (1404, 1412, 1414, 1504, 1513), so the §3 row's "slot 11" premise
    is wrong twice over;
  * the real remaining piece is 1404's 「充能达到 150 点时立即获得 1 个额外回合并自动施放【弑神登神】」. Its prerequisites: `EXTRA_TURN`
    EXISTS, but `SkillType` has no 强化战技 slot, and his TWO enhanced skills would both be addressed as `SKILL` -- so writing it that
    way would silently cast the wrong one. That is a blocker, not a shorthand, so it gets registered precisely instead;
  * and 1404's own register lists 「自身回合开始时自动施放【弑王成王】」 as missing while the rule `turn_start_autocasts_skill` ships.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
EXPRESSION = "EXPRESSION.md"

# ---- 1. the stale half of the register
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "level_convention")
note = rule["note"]
OLD = "自身回合开始时**自动施放【弑王成王】**；"
NEW = ("自身回合开始时**自动施放【弑王成王】**（✅ **已出货** ✓："
       "`turn_start_autocasts_skill` ⇒ `CAST_SKILL{skill: SKILL}` ✓ —— ⚠ 本行原本把它列成缺失 ✗，本轮订正 ✓）；")
if note.count(OLD) != 1:
    io.open("tools/_probe_register.txt", "w", encoding="utf-8", newline="\n").write(note)
    sys.exit("REFUSING: the register fragment appears %d times (the note is dumped for inspection)" % note.count(OLD))
rule["note"] = note.replace(OLD, NEW, 1)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404's register no longer calls the shipped half missing")

# ---- 2. the §3 row, rewritten from the measurements
lines = io.open(EXPRESSION, encoding="utf-8").read().split("\n")
PREFIX = "| 「自动施放【弑神登神】」"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「充能达到 150 点时，立即获得 1 个额外回合并自动施放【弑神登神】」**（万敌的**强化战技** ✓） "
    "| 一个**强化战技**的寻址方式 ✗ —— ⚠ 实测：`EXTRA_TURN` **已有** ✓，"
    "但 `SkillType` 没有强化战技槽 ✗，而他**两个**强化战技（弑王成王 ✓、弑神登神 ✓）"
    "会**同时**映射到 `SKILL` ✗ ⇒ 那样写会**惄惄施放错的那一个** ✗（不是简写 ✗） "
    "| `1404`（1 位）、**`1415` 的忆灵技能 8**（「使其自动施放1次不消耗充能的【弑神登神】」 ✓） **共 2 位** ✓ "
    "| `SkillType` 加一个强化战技槽，**或** 让 `CAST_SKILL` 按**数据行**寻址 ✓（⚠ 参考 `1404` 已出货的 "
    "`turn_start_autocasts_skill` ✓ —— 它读的是**它自己的数据行** ✓） |")
io.open(EXPRESSION, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row is corrected (1408 -> 1404's enhanced skill, two readers named)")
