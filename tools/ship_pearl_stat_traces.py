"""Ship Pearl's ten stat traces as content, because the panel path has no data for her.

Measured, in this order:
  * `AvatarSkillTreeConfig.json` rows for AvatarID 1503 DO carry them: ten rows with
    `StatusAddList = [{"PropertyType": ..., "Value": ...}]` - Defence 5/7.5/10%, Speed 2/3/4, effect RES 4/6%,
    Elation damage 4/6%. The property names map onto existing AttributeTypes (DefenceAddedRatio -> DEFENCE,
    SpeedDelta -> SPEED, StatusResistanceBase -> EFFECT_RESISTANCE, ElationDamageAddedRatioBase ->
    ELATION_DAMAGE_BOOST).
  * `src/main/resources/data/skill_traces.json` has 93 entries and 1503's list is EMPTY (1502/1505/1001 each have
    13). `SkillTrace` is what puts these onto the panel, so Pearl's panel is short those ten rows.
  * The corpus note says they are panel attributes handled by SkillTrace, so they need no content rules - which is
    true about the PATH and false about the DATA. That is the shape this project keeps catching: present in the dump,
    absent from the generated file.

So they are written as content, with the exact numbers from the rows:
    DEFENCE              0.05 + 0.075 + 0.10 = 0.225
    SPEED                2 + 3 + 4           = +9 (flat; SpeedDelta is an absolute delta)
    EFFECT_RESISTANCE    0.04 + 0.06         = 0.10
    ELATION_DAMAGE_BOOST 0.04 + 0.06         = 0.10

Usage:
    python tools/ship_pearl_stat_traces.py
    python tools/ship_pearl_stat_traces.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "stat_traces_the_ten_panel_rows"

SOURCE = (
    "1503 真珠 行迹属性（`AvatarSkillTreeConfig` 的 `AvatarID=1503` 行，`StatusAddList` 十条）："
    "防御力 +5.00%/+7.50%/+10.00%（1503201/1503205/1503210）、速度 +2/+3/+4（1503202/1503206/1503209）、"
    "效果抵抗 +4.00%/+6.00%（1503203/1503207）、欢愉度 +4.00%/+6.00%（1503204/1503208）。"
)

NOTE = (
    "⭐⭐ **这一条补的是一个实测出来的空洞**：语料说这十条属性行迹『已由 `SkillTrace` 处理，不需要内容规则』✓ —— "
    "那句话对**通路**是对的 ✓，对**数据**是错的 ✗。实测三件事：① `AvatarSkillTreeConfig` 里 `AvatarID=1503` "
    "**确实带着**这十条（`StatusAddList` 的 `PropertyType` 分别是 `DefenceAddedRatio`/`SpeedDelta`/"
    "`StatusResistanceBase`/`ElationDamageAddedRatioBase` ✓）；② 但引擎的行迹通路 `SkillTrace` 读的是**生成数据** "
    "`skill_traces.json` ✓，而那份文件有 **93** 个条目、`1502`/`1505`/`1001` **各 13 条**、**`1503` 是空表** ✗；"
    "③ 所以真珠的面板**少了这十条** ✓，而且**没有任何报错** ✓（面板只是偏低 ✓）。"
    "⇒ 这正是本项目反复抓到的那一型：**数据里有、生成文件里没有** ✓。"
    "⭐ 四条各写**一条效果**，数值是那十条的**和**（行迹是加算 ✓）：防御 `0.05+0.075+0.10 = 0.225` ✓、"
    "速度 `2+3+4 = 9`（`SpeedDelta` 是**绝对值** ⇒ 用 `amount` 而不是 `percent` ✓）、"
    "效果抵抗 `0.04+0.06 = 0.10` ✓、欢愉度 `0.04+0.06 = 0.10` ✓。"
    "⚠ 防御与欢愉度是**比率属性**（属性自己的单位是分数 ✓），所以 `percent: 0.225` 是『基准的 22.5%』，"
    "与数据行的 5%/7.5%/10% 同义 ✓。⚠ **修在内容侧的代价**：生成器补上这十条之后，这一条会与它**重复计算** ✓ —— "
    "所以解锁条件是『`skill_traces.json` 里 1503 的行数不再是 0』✓，那时**删掉这一条** ✓。"
    "⭐ 判据 `PearlStatTracesTest` 量的是**与数据行相符的绝对增量** ✓（不是『比 0 大』✓）。"
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
    if any(rule["id"] == RULE_ID for rule in data["rules"]):
        print("rule already present:", RULE_ID)
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    rule = {
        "on": "BATTLE_START",
        "id": RULE_ID,
        "do": [
            {"op": "MODIFY_ATTR", "attribute": "DEFENCE", "percent": 0.225, "permanent": True, "target": "self"},
            {"op": "MODIFY_ATTR", "attribute": "SPEED", "amount": 9.0, "permanent": True, "target": "self"},
            {"op": "MODIFY_ATTR", "attribute": "EFFECT_RESISTANCE", "percent": 0.10, "permanent": True, "target": "self"},
            {"op": "MODIFY_ATTR", "attribute": "ELATION_DAMAGE_BOOST", "percent": 0.10, "permanent": True, "target": "self"},
        ],
        "source": SOURCE,
        "note": NOTE,
    }
    before = len(data["rules"])
    data["rules"].append(rule)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == RULE_ID), None)
    if landed is None or len(landed["do"]) != 4:
        print("WRITE DID NOT LAND - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {before} -> {len(check['rules'])}")
    for effect in landed["do"]:
        print("  ", json.dumps(effect, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
