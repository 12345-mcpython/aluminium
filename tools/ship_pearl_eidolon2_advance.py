"""Ship Eidolon 2's widened action advance: the tier now reaches EVERY Elation ally, not just the archetype.

「施放终结技时，使除真珠和【美学底本】外的其他我方「欢愉」命途角色也触发行动提前效果」 (rank 150302, the first half of
Param [0.15, 1]).

The author's ruling on what "也" means: this is a change of TARGET, not an extra advance. At Eidolon 0-1 each tier
advances the archetype; from Eidolon 2 the same tier advances every ally on the Elation path. So:

  * the three shipped rules get `max_eidolon: 1`, which is why the engine needed a CEILING as well as a floor. Without
    it both sets fire and the archetype is advanced twice - and ADVANCE is multiplicative, so twice 10% is 19%, not
    20%. A silent wrong number, which is the shape this project refuses;
  * three new rules carry the same percentages and gates, with the target widened to the new selector
    `allies_of_path_except:欢愉:self,target` - "that Path, other than Pearl and other than the one she aimed at".

Note: The widening is stated as "other than Pearl and the archetype", which is exactly what the document says, and the
archetype is `target` on this event (the Ultimate is aimed at it). An archetype NOT on the Elation path therefore stops
being advanced from Eidolon 2 - which is what 「除…【美学底本】外」 says.

Usage:
    python tools/ship_pearl_eidolon2_advance.py
    python tools/ship_pearl_eidolon2_advance.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
SHIPPED = {
    1: "ult_advances_the_target_at_1_elation_allies",
    2: "ult_advances_the_target_at_2_elation_allies",
    3: "ult_advances_the_target_at_3_elation_allies",
}
PERCENT = {1: 0.1, 2: 0.15, 3: 0.3}
WIDE_SELECTOR = "allies_of_path_except:欢愉:self,target"

E2_SOURCE = ("1503 真珠 星魂 2 截取，顷刻破晓的光斑（rank 150302）: 「施放终结技时，使除真珠和【美学底本】外的其他我方"
             "「欢愉」命途角色也触发行动提前效果，…」—— 数值沿用大招那三档（10%/15%/30%）。")
E2_NOTE = ("⭐⭐ **这是一次「对象变更」，不是「再来一次提前」** ✓（作者裁决 ✓）：E0–E1 时该档位只提前**底本** ✓，"
           "E2 起同一档位提前**全部欢愉命途的我方** ✓ ⇒ 因此**已出货那三条必须加 `max_eidolon: 1`** ✓ —— "
           "这正是引擎需要**上限**而不只是下限的原因 ✓：只加下限会让**两套同时触发** ✓，而底本被提前**两次** ✓，"
           "而 `ADVANCE` 是**乘法叠加** ✓（`remaining × (1 − p)` ✓）⇒ 两次 10% 是 **19% 而不是 20%** ✓ "
           "—— 一个**没有症状的错数** ✓，正是本项目拒绝的东西 ✓。"
           "⭐ 新选择器 `allies_of_path_except:欢愉:self,target` ✓ = 「该命途的、除真珠与**她指定的那个**之外的我方」 ✓ "
           "—— 本事件上底本**就是** `target` ✓（大招指向它 ✓）。")


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
    ids = [r["id"] for r in rules]
    # Note: Match the SHIPPED ids' own `_at_e2` suffix, not any id ending in `_at_e2` - the extra-turn reward's
    # Eidolon-2 clause uses that suffix too, and a looser guard refused to ship this one.
    if any(rid + "_at_e2" in ids for rid in SHIPPED.values()):
        print("already present")
        return 2
    missing = [rid for rid in SHIPPED.values() if rid not in ids]
    if missing:
        print("the shipped advance rules are missing:", missing)
        return 3

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(rules)
    # 1) the shipped three stop at Eidolon 1
    for tier, rid in SHIPPED.items():
        rule = next(r for r in rules if r["id"] == rid)
        rule["max_eidolon"] = 1
        rule["note"] = ((rule.get("note") or "")
                        + "\n  ⚠ **本条加了 `max_eidolon: 1`**（随星魂 2 出货 ✓）：星魂 2 把该档位的提前对象"
                          "从**只底本**扩大到**全部欢愉角色** ✓ ⇒ 本条在 E2 起**必须停用** ✓，否则底本被提前两次 ✓"
                          "（`ADVANCE` 是乘法叠加 ⇒ 两次 10% = 19% ✓）。")
    # 2) the Eidolon-2 trio, same tiers and percentages, widened target
    for tier, rid in SHIPPED.items():
        rules.append({
            "on": "ULT_CAST",
            "id": rid + "_at_e2",
            "min_eidolon": 2,
            "when": [
                "actor == self",
                f"allies_with_path:欢愉 >= {tier}"
            ],
            "do": [
                {"op": "ADVANCE", "percent": PERCENT[tier], "target": WIDE_SELECTOR}
            ],
            "source": E2_SOURCE,
            "note": E2_NOTE,
        })
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    problems = []
    for tier, rid in SHIPPED.items():
        base = next((r for r in check["rules"] if r["id"] == rid), None)
        if base is None or base.get("max_eidolon") != 1:
            problems.append(f"{rid} ceiling reads {None if base is None else base.get('max_eidolon')}")
        wide = next((r for r in check["rules"] if r["id"] == rid + "_at_e2"), None)
        if wide is None:
            problems.append(f"{rid}_at_e2 did not land")
        elif wide["do"][0].get("target") != WIDE_SELECTOR or wide["do"][0].get("percent") != PERCENT[tier]:
            problems.append(f"{rid}_at_e2 reads {wide['do'][0]}")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    for tier, rid in SHIPPED.items():
        print(f"  {rid}: max_eidolon=1 ; {rid}_at_e2: target={WIDE_SELECTOR} percent={PERCENT[tier]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
