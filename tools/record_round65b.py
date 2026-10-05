"""Replace the healing bullet by FINDING its line, not by matching its whole text (2026-10-02).

The last two attempts refused because the bullet's exact wording differs from what I typed into the anchor. Finding the line that contains the distinctive phrase, then replacing
that whole line, cannot drift.
"""
import io
import sys

GAPS = "GAPS.md"
lines = io.open(GAPS, encoding="utf-8").read().split("\n")

NEEDLE = "计入小伊卡"
hits = [i for i, l in enumerate(lines) if NEEDLE in l]
print("lines mentioning the healing total: %s" % [i + 1 for i in hits])
if len(hits) != 1:
    sys.exit("REFUSING: %d lines mention it" % len(hits))

NEW = ("- ⛔「计入小伊卡忆灵技的治疗数值额外提高…等同于本次治疗数值的 `#1%`」："
       "**载体已经找到了，卡在别处**（2026-10-02 实测）：\n"
       "  - ✅ `HEALED` **带量**（`fireTriggersForAlly(TriggerEvent.HEALED, healer, target, healed)`），"
       "且该事件的 **actor 是治疗者** ⇒「风堇…提供治疗」可用 `actor has_state`；\n"
       "  - ✅ `GAIN_RESOURCE` 已支持 `amount_from_event` × `amount_percent`（字面量）；\n"
       "  - ⛔ **缺口一**：「累计治疗数值」属于**风堇的小伊卡**，而选择器只能点名**角色**"
       "（`ally_cid:`），点不了**别人的忆灵** ⇒ 需要「某角色的忆灵」这样的选择器；\n"
       "  - ⛔ **缺口二**：`characters/1409.json` 是**列表**，装不下 `resources` ⇒ 需要资源声明有别的落点；\n"
       "  - ⛔ **缺口三**：`amount_percent` 只能是字面量，而 `#1` 随等级变（0.36 → 1.008）"
       "⇒ 需要 `amount_percent_from_skill_param`（本轮做了又回滚：判据在 `BATTLE_START` 期间由 `HEAL` 触发的 "
       "`HEALED` 上读数仍为 0，⭐ **下一问**：那次 `GAIN_RESOURCE` 到底有没有被走到）。")

lines[hits[0]] = NEW
io.open(GAPS, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("GAPS.md bullet replaced (line %d)" % (hits[0] + 1))

BLOCK = io.open("tools/_block65.txt", encoding="utf-8").read()
with io.open("GAPS_LOG.md", "a", encoding="utf-8", newline="\n") as handle:
    handle.write(BLOCK)
print("GAPS_LOG.md appended")
