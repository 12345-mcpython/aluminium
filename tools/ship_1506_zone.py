"""1506's ZONE (结界), written the plain way: one-line notes, no concatenation, no inner straight quotes."""

import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
ZONE = "结界"

NOTE_ZONE = (
    "⚠⚠ 此前把这一条登记成「不可实现」，那是错的 —— 结界在本项目里是一个命名状态标记，不是需要新建的区域容器。"
    "实测：1203 的天赋 talent_zone_opens_at_two_stacks 就是 APPLY_BUFF buff 结界，而它下一条从句用 target has_state 结界 读它；"
    "1410 的 ult_zone_opens_and_lowers 还带 buff 结界 加 ticks_on self；1513 同样在用。"
    "⭐ 教训：写下「需要 X 机制」之前，先搜 X 在已出货内容里怎么出现 —— 我凭「引擎里没有 Zone 类」就下了结论，"
    "而内容是按名字与状态表达它的。"
)

NOTE_LOOT = (
    "⭐⭐ 「每消耗 1 个战技点」当时就能写 —— 实测 SKILL_POINT_SPENT 是真实事件，与先前登记的「没有战技点消耗事件」相反。"
    "⚠ 伤害按 all_enemies 结算（原文「由敌方全体均分」）。这是读法选择：引擎没有「把总量均分」的原语，"
    "而「每个目标各受 90%」与「总量 90% 均分」只在敌人数为 1 时相同 —— 如实写下，不埋着。"
    "⚠⚠ 概率那一半未写：「初始固定概率 100%、成功触发后降到当前的 20%」需要一个会衰减的规则概率，"
    "而引擎没有规则层 chance 的修正器（MODIFY_RULE 抬的是效果层 base_chance，那是施加负面效果的概率）。"
    "⇒ 静态写 1.0 会每次都触发、静态写 0.2 会在错的时刻触发 ⇒ 两者都不是「衰减概率」的诚实读法，登记。"
    "⚠ 三选一的随机效果同样登记（与 21032 的随机效果选择同一缺口）。"
)

RULES = [
    {
        "on": "ULT_CAST",
        "id": "p1506_ult_deploys_the_zone",
        "when": ["actor == self"],
        "do": [{"op": "APPLY_BUFF", "buff": ZONE, "permanent": True, "target": "self"}],
        "source": "1506 银狼LV.999 终结技 无敌玩家，启动！ (150603): 「【无敌玩家】状态下展开结界。」",
        "note": NOTE_ZONE,
    },
    {
        "on": "SKILL_POINT_SPENT",
        "id": "p1506_zone_loot_box_on_a_spent_point",
        "when": ["self has_state 结界", "self has_state 无敌玩家"],
        "do": [{
            "op": "DAMAGE",
            "scale": "self_attr:ATTACK",
            "percent": 0.9,
            "element": "Imaginary",
            "damage_type": "ELATION",
            "target": "all_enemies",
        }],
        "source": "1506 银狼LV.999 终结技 无敌玩家，启动！ (150603): 「结界内我方目标每消耗 1 个战技点，有概率触发 1 次"
                  "【头号补给盲盒】：造成 90% 的虚数属性欢愉伤害，由敌方全体均分」—— #3 = 0.9。",
        "note": NOTE_LOOT,
    },
]


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup")
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored")
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written")

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    before = len(data["rules"])
    have = {r["id"] for r in data["rules"]}
    for rule in RULES:
        if rule["id"] in have:
            print("already present:", rule["id"])
            continue
        data["rules"].append(rule)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = {r["id"] for r in check["rules"]}
    missing = [r["id"] for r in RULES if r["id"] not in landed]
    if missing:
        print("WRITE CHECK FAILED:", missing)
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
