"""Correct the two notes in 1415.json: the LATER writer is the one that must be stackable (2026-10-02).

Measured chain, now closed:
  * `BuffManager.addBuff` branches on the INCOMING buff: `if (buff.isStackable()) { addStackable(buff); return; }`,
    and only a NON-stackable incomer walks the `isSameKind` replace loop;
  * `StatModifierBuff.isStackable()` is exactly `maxStacks > 1`;
  * so the later writer needs `max_stacks >= 2`; the earlier one's value does not matter (measured: mutating the talent's
    value to 1 left the reading at 0.4).
Earlier notes here said both sides must state it, and one blamed `permanent`. Both claims are withdrawn and replaced.

The reading this note has to agree with: 0.2 below her threshold, 0.4 past it.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1415.json"

NEW_TALENT_NOTE = (
    " ⭐ 2026-10-02 实测：`\"max_stacks\": 2` ✓ —— 同属性多来源能不能相加，"
    "**看的是后写的那一条** ✓（`BuffManager.addBuff` 先判 `buff.isStackable()` ✓，"
    "只有**不可叠加**的来者才走 `isSameKind` 替换循环 ✓）；"
    "⭐ 而 `StatModifierBuff.isStackable()` 就是 `maxStacks > 1` ✓。"
    "⚠ 本条（入场更早的那个）写多少**不影响**结果 ✓："
    "把它改成 1 后读数**仍是 0.4** ✓（已测 ✓）。")

NEW_TRACE_NOTE = (
    "⭐ 与天赋同属性 ✓ ⇒ **它是后写的那一条** ✓ ⇒ 必须 `\"max_stacks\": 2` ✓"
    "（实测：它写 1 时读数回到 **0.2** ✗ —— 不可叠加的来者会把天赋那条**替换掉** ✓）。"
    "⚠ 与 `permanent` **无关** ✗（该猜测已证伪 ✓）。")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
changed = 0

for r in rules:
    if not isinstance(r, dict):
        continue
    if r.get("id") == "talent_party_damage":
        note = r.get("note") or ""
        marker = " ⭐ 2026-10-02"
        if marker in note:
            note = note[:note.index(marker)]
        r["note"] = note + NEW_TALENT_NOTE
        changed += 1
    elif r.get("id") == "trace_party_damage_at_speed_180":
        r["note"] = NEW_TRACE_NOTE
        changed += 1

print("notes rewritten:", changed)
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json notes carry the corrected mechanism")
