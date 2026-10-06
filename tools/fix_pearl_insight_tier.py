"""Merge Pearl (1503)'s two 洞察万物 tiers into ONE rule, because two same-attribute modifiers
were measured to REPLACE each other.

What happened: the first version shipped two `MODIFY_ATTR` on `ELATION_DAMAGE_BOOST` (a flat 0.32 and
`self_attr_above:DEFENCE:2400` + `amount: -0.04`). At DEF 2400 the reading was **0.28, not 0.32** - the
second, same-kind modifier had replaced the first (default `max_stacks: 1`, an established convention in
this engine), because at exactly 2400 the excess is 0 so the growth rule computed `0 - 0.04 = -0.04`.

The merge is not a workaround: `self_attr_above:DEFENCE:2400` already takes `max(0, DEF - 2400)`, so one
rule reads `0.32 + 0.0003 x excess` and `cap_amount: 0.68` states the documented ceiling on the OUTPUT,
which is equivalent to "count at most 3600 points of excess":
    0.32 + 0.0003 x (3600 - 2400) = 0.32 + 0.36 = 0.68.

Usage:
    python tools/fix_pearl_insight_tier.py          # apply
    python tools/fix_pearl_insight_tier.py restore  # revert
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
GROWTH_ID = "trace_insight_grows_per_hundred_defence_over_2400"
BASE_ID = "trace_insight_raises_elation_at_2400_defence"

MERGED_NOTE = (
    "⭐⭐ **一条规则，不是两条** —— 第一版写成两条（平的 0.32 与 `self_attr_above:DEFENCE:2400` + `amount: -0.04`），"
    "而**实测在 DEF = 2400 处读到 0.28 而不是 0.32** ✗：两条同属性、同类的修饰**互相替换**（`max_stacks` 默认 1，"
    "这是引擎的既有口径），后一条在超出量为 0 时给出 `0 − 0.04`，把前一条顶掉了 ✓。"
    "⭐ 合并**不是绕道** ✓：`self_attr_above:DEFENCE:2400` 本身就取 `max(0, DEF − 2400)` ✓，"
    "所以一条规则读 `0.32 + 0.0003 × 超出量` ✓，而 `cap_amount: 0.68` 正好写出文档的上限 —— "
    "「最多计入 3600 点超出的防御力」⇒ `0.32 + 0.0003 × (3600 − 2400) = 0.68` ✓。"
    "⇒ 上限这次是**按输出端**表达的，而它与文档的读法在**任何** DEF 上都相等 ✓（写 `amount` 那种输出端偏移才会分岔 ✓）。"
    "⭐ 判据 `PearlInsightTraceTest` 量四点：2300 给 0 ✓、2400 给 0.32 ✓、2500 给 0.35 ✓、"
    "4000 与 5000 **读数相同**（封顶 0.68 ✓）。"
)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [rule["id"] for rule in rules]
    if BASE_ID not in ids or GROWTH_ID not in ids:
        print("expected both tier rules to exist; base:", BASE_ID in ids, "growth:", GROWTH_ID in ids)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(rules)

    # 1. The merged rule: threshold + excess + output ceiling, all in one.
    for rule in rules:
        if rule["id"] == BASE_ID:
            rule["do"][0]["scale"] = "self_attr_above:DEFENCE:2400"
            rule["do"][0]["percent"] = 0.0003
            rule["do"][0]["amount"] = 0.32
            rule["do"][0]["cap_amount"] = 0.68
            rule["note"] = MERGED_NOTE

    # 2. Drop the now-redundant growth rule.
    data["rules"] = [rule for rule in rules if rule["id"] != GROWTH_ID]

    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    TARGET.write_text(text, encoding="utf-8")

    # Prove the write landed and is parseable: a silent no-op reads as "fixed".
    check = json.loads(TARGET.read_text(encoding="utf-8"))
    left = [rule["id"] for rule in check["rules"]]
    problems = []
    if GROWTH_ID in left:
        problems.append("the growth rule is still there")
    if before - len(left) != 1:
        problems.append(f"expected to drop exactly one rule, dropped {before - len(left)}")
    merged = next((rule for rule in check["rules"] if rule["id"] == BASE_ID), None)
    if merged is None or merged["do"][0].get("cap_amount") != 0.68:
        problems.append("the merged rule does not carry cap_amount 0.68")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3

    print(f"rules: {before} -> {len(left)}")
    print("merged into", BASE_ID, ":", json.dumps(merged["do"][0], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
