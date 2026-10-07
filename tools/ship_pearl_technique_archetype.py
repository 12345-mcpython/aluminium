"""Give Pearl's technique its other two halves: mark the archetype, and apply 深度学习 to whoever holds it.

The document: 「使用秘技后获得【美学底本】，切换行动角色时【美学底本】转移至当前行动角色。下一次战斗开始时，真珠获得 20 点
【好活当赏】，并【深度学习】持有【美学底本】的角色，该【深度学习】具有 2 点充能。」

What was shipped was only the 20 points. So the technique's headline effect - her 深度学习 landing on an ally before the
first turn - did not happen at all, and the 美学底本 the technique grants did not exist either.

The 美学底本 itself is applied by `Battle.applyTechniqueStates` from the unit the caller names with
`markArchetypeHolder`, because 「切换行动角色」 happens on the map and the engine cannot observe it - the same shape the
technique already uses (`markTechniqueUsed`). This script adds the RULE half: at the start of the battle, apply
深度学习 to whoever holds the mark.

Usage:
    python tools/ship_pearl_technique_archetype.py
    python tools/ship_pearl_technique_archetype.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "technique_grants_the_opening_counter"
NEW_ID = "technique_deep_studies_the_archetype_holder"

SOURCE = ("1503 真珠 秘技 凝润珠光，复刻真迹 (150307): 「使用秘技后获得【美学底本】，切换行动角色时【美学底本】转移至当前行动角色。"
          "下一次战斗开始时，真珠获得 #2[i] 点【好活当赏】，并【深度学习】持有【美学底本】的角色，该【深度学习】具有 #1[i] 点充能」"
          "—— #2 = 20、#1 = 2。")

RULE = {
    "on": "BATTLE_START",
    "id": NEW_ID,
    "when": [
        "self has_state 秘技"
    ],
    "do": [
        {
            "op": "APPLY_BUFF",
            "buff": "深度学习",
            "turns": 2,
            "target": "holder_of:美学底本"
        }
    ],
    "source": SOURCE,
    "note": "⭐⭐ **补上秘技缺失的那一半** ✓：此前这条秘技**只给了 20 点【好活当赏】** ✓，"
            "而原文还说「获得【美学底本】」＋「并【深度学习】**持有【美学底本】的角色**」✓ ⇒ "
            "**她的招牌效果（开局就把【深度学习】落到队友身上）根本没有发生** ✓，"
            "而且秘技给的那个【美学底本】**也不存在** ✓ —— 那是一个**沉默缺口** ✓。"
            "⚠ **两件事分别落地** ✓：① 【美学底本】由引擎在**战前**挂上 ✓"
            "（`Battle.markArchetypeHolder` ＋ `applyTechniqueStates` ✓）—— 因为「**切换行动角色**」发生在**地图上** ✓，"
            "引擎**观察不到它** ✓，与秘技本身同形 ✓（秘技也是调用方用 `markTechniqueUsed` 告知的 ✓）；"
            "② 【深度学习】由**本条规则**挂到**持有底本的那个单位**上 ✓。"
            "⭐ 目标用 `holder_of:美学底本` ✓ —— 它读的是**谁持有那个状态** ✓，与量值拼法 `holder_attr:` 共用同一处查找 ✓。"
            "⚠ `turns: 2` 来自秘技行的 `#1 = 2`（原文说的是「**充能**」2 点 ✓）—— ⚠ 这是一个**登记在案的近似** ✓："
            "引擎暂无「按使用次数」的时长 ✓，而回合数是它现在表达时长的唯一方式 ✓。",
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    ids = [r["id"] for r in data["rules"]]
    if NEW_ID in ids:
        print("already present")
        return 2
    if RULE_ID not in ids:
        print("the technique rule is missing:", RULE_ID)
        return 3

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == NEW_ID), None)
    if landed is None:
        print("WRITE DID NOT LAND - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    effect = landed["do"][0]
    problems = []
    if effect.get("buff") != "深度学习":
        problems.append(f"buff reads {effect.get('buff')}")
    if effect.get("target") != "holder_of:美学底本":
        problems.append(f"target reads {effect.get('target')}")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 5
    print(f"rules: {before} -> {len(check['rules'])}")
    print("  +", json.dumps(effect, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
