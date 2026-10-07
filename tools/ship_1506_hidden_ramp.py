"""1506's enhanced-basic ramp: 「每拥有 60 点【隐藏分】，强化普攻期间造成的伤害提高原伤害的 15%，最多叠加 2 层」.

min(floor(hidden / 60), 2) * 0.15 - a plateau at one and at two layers. Decomposed into four threshold terms, because the
shipped vocabulary is linear and this function is not:

    hidden < 60     nothing
    60 .. 119       0.15
    120 .. 179      -0.075 + 0.075 = 0.15      (the plateau: a +0.075 term at 120 cancels the +0.075 still running)
    >= 180          -0.075 + 0.075 + 0.15 = 0.30   (the cap: a +0.15 term at 180, and nothing above it)

Every term is `self_attr_above:<RESOURCE>:<threshold>`-shaped, i.e. the threshold spelling the engine already has, and
`self_resource:` is one of the resource readouts it accepts.

Carried on ALL_DAMAGE_TYPE_BOOST, which is read per damage INSTANCE, so the effect lands on the enhanced basic's damage
and on nothing else - provided the gate identifies that cast. The gate is `from_skill_id == 8.0`, the enhanced basic's own
slot, because `ATTACK_FINISHED` and the skill-damage path do carry it (unlike the state-ending rule, which had to be gated
on the state itself).
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "p1506_enhanced_basic_hidden_ramp"
HIDDEN = "隐藏分"

GATE = ["actor == self", "from_skill_id == 8.0"]


def term(threshold, percent):
    return {
        "op": "MODIFY_ATTR",
        "attribute": "ALL_DAMAGE_TYPE_BOOST",
        "scale": f"self_attr_above:{HIDDEN}:{threshold}",
        "percent": percent,
        "permanent": True,
        "coexist": True,
        "target": "self",
    }


RULE = {
    "on": "DEALING_DAMAGE",
    "id": RULE_ID,
    "when": GATE,
    "do": [
        term(60, 0.15),
        term(120, -0.075),
        term(120, 0.075),
        term(180, 0.15),
    ],
    "source": "1506 银狼LV.999 强化普攻 奖励关：「狼尊时刻」: 「每拥有 60 点【隐藏分】，强化普攻期间造成的伤害提高原伤害的"
              "15%，最多叠加 2 层」—— #6 = 60、#8 = 0.15、#7 = 2。",
    "note": "⭐⭐ **分段函数用四个线性项拼出来** ✓ —— `min(floor(隐藏分/60), 2) × 15%` **不是线性的** ✓，"
            "而词汇表里**只有线性** ✓ ⇒ 四个阈值项相加 ✓ 恰好复现它 ✓："
            "⚠ <60 无 ✓；60–119 得 **0.15** ✓；120–179 得 `−0.075 + 0.075 = 0.15` ✓（"
            "**平台**：120 那个 `+0.075` 恰好抵消仍在跑的 `+0.075` ✓）；≥180 得 `−0.075 + 0.075 + 0.15 = 0.30` ✓（"
            "**封顶**：180 那个 `+0.15` 之后**再没有项** ✓）。"
            "⚠ 每一项都是 `self_attr_above:<资源>:<阈值>` ✓ —— **阈值拼法是既有的** ✓，而 `self_resource:` 是它接受的读数之一 ✓。"
            "⚠ 载体用 `ALL_DAMAGE_TYPE_BOOST` ✓（**按伤害实例读** ✓ ⇒ 只落在那次强化普攻上 ✓），"
            "门用 `from_skill_id == 8.0` ✓（**强化普攻自己的槽位** ✓）—— ⚠ 与退出【无敌玩家】那条**不同** ✓："
            "那条挂在 `ATTACK_FINISHED` 上 ✗ 而**该事件不携带槽位** ✓；这条挂在**伤害路径**上 ✓，槽位在那里是有的 ✓。"
            "⚠ 四个项**必须都带 `coexist`** ✓ —— 同属性同目标的两个修正器会被判同类而互相驱逐 ✓（速度行迹那条踩过 ✓）。",
}


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
    if any(r["id"] == RULE_ID for r in data["rules"]):
        print("already present")
        return 0
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == RULE_ID), None)
    if landed is None or len(landed["do"]) != 4:
        print("WRITE CHECK FAILED")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(check['rules'])}")
    for effect in landed["do"]:
        print("   ", effect["scale"], "->", effect["percent"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
