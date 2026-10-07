# -*- coding: utf-8 -*-
"""Author light cone 21032 「秘密」 - the one-of-three that replaces the last effect and never repeats it.

Read out of the game's own ability data (`ConfigAbility/EquipmemtAbility.json`, `Ability21032`) rather than from the text
alone:

  * `MEquip_21032_Main` on `OnEnterBattle` (wave 1) rolls `RandomConfig` with odds 0.333/0.333/0.334 and adds ONE of
    `MEquip_21032_Sub1/2/3` - the first pick is UNIFORM;
  * each `Sub` carries the current choice: `OnStack` applies its `Buff` to `AllTeamMember`, and `OnPhase1` (the wearer's
    turn) REMOVES its own buff and rolls 0.5/0.5 over the other TWO subs, then removes itself. So "替换上次且不与上次重复"
    is: drop the current effect, pick uniformly among the remaining ones;
  * the three buffs are `AttackAddedRatio` / `CriticalDamageBase` / `SPRatioBase` on `AllTeamMember`, which this project
    spells `ATTACK` / `CRIT_ATTACK` / `ENERGY_REGENERATION_RATE` (the mapping is already in `AttributeType`);
  * all five ranks state the same numbers (the document has no rank placeholder), so every rank gets the same rules.

The two triggers cannot be one rule here: `BATTLE_START` is fired with no actor, so a rule carrying `actor == self` for
the wearer's turn cannot also catch the battle's start. They share the pick memory BY NAME instead
(`one_of_no_repeat`), which is exactly what the game's single state modifier does.

Usage:
    python tools/ship_cone_21032.py
"""
import io
import json
import pathlib
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TARGET = pathlib.Path('src/main/resources/light_cones/21032.json')
REGISTRY = pathlib.Path('src/main/resources/light_cones/_unmodelled.json')
RANKS = ['1', '2', '3', '4', '5']
MEMORY = 'cone21032_secret'
ATK, CRIT, ENERGY = '秘密-攻击力', '秘密-暴击伤害', '秘密-能量恢复'

SOURCE = ('光锥 21032 秘密: 「战斗开始时以及当装备者回合开始时，随机生效 1 个效果。该效果生效时，替换上次的效果且本次不会'
          '与上次重复。效果包含：我方全体攻击力 +10%；暴击伤害 +12%；能量恢复效率 +6%。」')

NOTE = ('⭐⭐ **由登记表转正** ✓（登记过两次 ✓：先是「随机选择 + 去重」✗，后是「需要一个"上次选中"的资源」✗）：'
        '⭐ 机制在**上游自己的脚本**里读到了（`ConfigAbility/EquipmemtAbility.json` 的 `Ability21032` ✓）：'
        '`MEquip_21032_Main` 在 `OnEnterBattle`（第 1 波 ✓）用 `RandomConfig` 按 **0.333/0.333/0.334** 抽一个 ✓'
        '（⇒ **第一次是均匀三选一** ✓，作者确认 ✓），而每个 `MEquip_21032_SubN` 就是"当前选择"本身 ✓ —— '
        '它的 `OnPhase1`（装备者回合 ✓）**先移除自己的 buff** ✓，再在**另外两个**之间按 **0.5/0.5** 抽 ✓，然后 `RemoveSelfModifier` ✓ '
        '⇒ 「替换上次且本次不会与上次重复」= **去掉当前那个、在剩下的里均匀抽** ✓。'
        '⭐ 三条 buff 的属性名与项目一一对应 ✓：`AttackAddedRatio`／`CriticalDamageBase`／`SPRatioBase` '
        '= `ATTACK`／`CRIT_ATTACK`／`ENERGY_REGENERATION_RATE` ✓（映射表就在 `AttributeType` ✓），目标 `AllTeamMember` = `all_allies` ✓。'
        '⚠⚠ **为什么是两条规则、却共享一个记忆** ✓：`BATTLE_START` 是**没有 actor** 发的 ✓ ⇒ 「装备者回合开始时」那条的 '
        '`actor == self` **在战斗开始时必假** ✗ ⇒ 两者不能合成一条 ✓；而文档要求**回合开始的第一次必须与战斗开始时不同** ✓ ⇒ '
        '用 `one_of_no_repeat` 的**名字**把两条的记忆连起来 ✓（这与上游"一个状态修饰器携带当前选择"完全同形 ✓）。'
        '⚠ **替换的写法** ✓：三条效果各带**名字** ✓（`buff` ✓）且 `permanent: true` ✓（「持续到下一次触发」没有固定回合数 ✓ —— '
        '`REMOVE_STATE` 按名字移除**永久**修饰器 ✓，实测见判据 ✓），规则的效果表是**三条 `always` 的移除（`kind: own` ✓）'
        '＋三条待抽的挂载** ✓ ⇒ 每次触发后场上**恰好一条** ✓。⚠ **五阶数值相同** ✓（文档没有 #N[i] 位 ✓）⇒ 五阶写同样的规则 ✓。')


def removals():
    return [{"op": "REMOVE_STATE", "buff": name, "kind": "own", "target": "all_allies", "always": True}
            for name in (ATK, CRIT, ENERGY)]


def picks():
    return [
        {"op": "MODIFY_ATTR", "attribute": "ATTACK", "percent": 0.10, "permanent": True,
         "buff": ATK, "target": "all_allies"},
        {"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.12, "permanent": True,
         "buff": CRIT, "target": "all_allies"},
        {"op": "MODIFY_ATTR", "attribute": "ENERGY_REGENERATION_RATE", "percent": 0.06, "permanent": True,
         "buff": ENERGY, "target": "all_allies"},
    ]


def rules():
    battle_start = {
        "on": "BATTLE_START",
        "id": "cone21032_battle_start",
        "one_of": True,
        "one_of_no_repeat": MEMORY,
        "do": removals() + picks(),
        "source": SOURCE,
        "note": NOTE,
    }
    turn_start = dict(battle_start)
    turn_start["on"] = "TURN_START"
    turn_start["id"] = "cone21032_wearer_turn_start"
    turn_start["when"] = ["actor == self"]
    turn_start["note"] = NOTE + ('⚠ 本条的 `actor == self` 就是「**装备者**回合开始时」✓（与 21021 同一条既有写法 ✓）；'
                                 '⚠ 它**不再**有条件说"现在挂着的是哪一个" ✓ —— 那是 `one_of_no_repeat` 的记忆 ✓。')
    return [battle_start, turn_start]


def main():
    table = {rank: rules() for rank in RANKS}
    TARGET.write_text(json.dumps(table, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('wrote', TARGET, '(%d ranks)' % len(table))

    registry = json.loads(REGISTRY.read_text(encoding='utf-8'))
    if '21032' in registry:
        del registry['21032']
        REGISTRY.write_text(json.dumps(registry, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('removed 21032 from', REGISTRY, '- still registered:', sorted(registry))
    return 0


if __name__ == '__main__':
    sys.exit(main())
