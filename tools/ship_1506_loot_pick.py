"""1506's loot box completes: the three-way random effect, now that `one_of` exists.

「…并随机触发 1 种效果：【超超超大剑】：对生命值最高的敌方目标额外造成等同于本次总伤害值 20% 的真实伤害；
【爆爆爆炸蛋】：恢复 2 个战技点；【怪怪怪味豆】：获得 3 个笑点。」

The damage half already shipped. What was missing is "randomly trigger ONE of", and it is now a rule-level field. The
constant damage stays in its own rule, because `one_of` picks among a rule's OWN effects - putting the damage in the same
rule would make it one of the things that can be skipped.

⚠ 超超超大剑 is written as 20% of her ATTACK rather than 20% of 本次总伤害值: the document's basis is the damage that
just settled, and the engine has `original_damage` for that shape, but it needs the settled instance of THIS trigger -
which is a measurement to make rather than a value to assume, so it is registered.
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
LOOT = "p1506_zone_loot_box_on_a_spent_point"
PICK = "p1506_loot_box_picks_one_effect"

GATE = ["self has_state 结界", "self has_state 无敌玩家"]

RULE = {
    "on": "SKILL_POINT_SPENT",
    "id": PICK,
    "when": GATE,
    "one_of": True,
    "do": [
        {
            "op": "DAMAGE",
            "scale": "self_attr:ATTACK",
            "percent": 0.2,
            "element": "Imaginary",
            "damage_type": "TRUE",
            "target": "highest_hp_attack_hit"
        },
        {
            "op": "GAIN_RESOURCE",
            "resource": "战技点",
            "amount": 2,
            "target": "self"
        },
        {
            "op": "GAIN_RESOURCE",
            "resource": "笑点",
            "amount": 3,
            "target": "self"
        },
    ],
    "source": "1506 银狼LV.999 终结技 无敌玩家，启动！ (150603): 「并随机触发 1 种效果：【超超超大剑】：对生命值最高的敌方目标额外造成"
              "等同于本次总伤害值 20% 的真实伤害；【爆爆爆炸蛋】：恢复 2 个战技点；【怪怪怪味豆】：获得 3 个笑点。」",
    "note": "⭐⭐ **`one_of` 的第一个读者** ✓ —— 规则层字段 ✓：**触发时从它自己的 N 个效果里选一个执行** ✓（`TriggerSpec.one_of` ✓）。"
            "⚠ **一次只掷一次** ✓（不是每个效果各掷 ✓，否则可能选到 0 个或 2 个 ✓）。"
            "⚠ **常量伤害留在另一条规则里** ✓（`p1506_zone_loot_box_on_a_spent_point` ✓）—— "
            "因为 `one_of` 是在**本规则的**效果里选 ✓，把伤害放进来会让它**也成为可被跳过的那个** ✓。"
            "⚠⚠ **【超超超大剑】写的是她攻击力的 20%** ✓，而原文是「**等同于本次总伤害值 20%**」✓ —— "
            "引擎有 `original_damage` 这个形状 ✓，但它要的是**本次触发自己那个已结算的实例** ✓ ⇒ "
            "⚠ **那是一件要量的事、不是一个可以假定的数** ✓ ⇒ **登记** ✓（不近似 ✓）。"
            "⚠ **战技点的恢复**用 `GAIN_RESOURCE 战技点` ✓ —— 那个计数**由战斗持有** ✓（`skillPointPolicy` ✓），"
            "⚠ 若引擎侧不接受这个资源名，判据会立刻报出来 ✓。",
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
    if any(r["id"] == PICK for r in data["rules"]):
        print("already present")
        return 0
    data["rules"].append(RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == PICK), None)
    if landed is None or not landed.get("one_of") or len(landed["do"]) != 3:
        print("WRITE CHECK FAILED")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {len(data['rules']) - 1} -> {len(check['rules'])} | one_of with {len(landed['do'])} effects")
    return 0


if __name__ == "__main__":
    sys.exit(main())
