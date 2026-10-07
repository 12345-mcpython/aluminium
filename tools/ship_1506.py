"""Ship 1506 (Silver Wolf LV.999): the parts of her kit that are writable today.

Reading her page (583 lines) against the 2 rules that exist. The plan is in tools/_1506_plan.txt; this script writes only
the rows marked WRITE, and leaves the three blocked ones (结界/区域, 控制免疫, 封顶缩放) registered.

Shipped here:
  * 笑点 declared as a party counter (she gains it; the talent turns it into 隐藏分);
  * 战技 gains 5 笑点 (slot 2);
  * 欢愉技 gains 15 隐藏分 (slot 20);
  * 终结技 enters 【无敌玩家】, which is what makes her the character;
  * 天赋: 强化普攻 charges and leaving 【无敌玩家】 after 3 uses, clearing 隐藏分 as she leaves;
  * 天赋: while holding 【好活当赏】, her Basic and Skill add 40% ATK Imaginary Elation damage;
  * 行迹: the elation-skill trace (>= 20 counted laughs -> +20 隐藏分, >= 40 -> another +20);
  * 星魂 4: 【崩坏级伤害演示】counts 5x the laughs (an extra additive rider on slot 21).

⚠ NOT shipped, and why (registered in the relic-style _unmodelled spirit, in GAPS):
  * 结界 (the Zone) - the engine has no region mechanism, and 战技点-consumption events do not exist either, so
    【头号补给盲盒】 has no trigger;
  * 控制类负面状态 immunity - no crowd-control marker exists in the engine;
  * the 隐藏分 -> CRIT conversion with its cap - 「暴击率达到 100% 后，其余每点改为暴击伤害」 needs a scaling clamped
    at a threshold, and no spelling clamps a scale's result.

Usage:
    python tools/ship_1506.py
    python tools/ship_1506.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")

LAUGH = "笑点"
HIDDEN = "隐藏分"
GOD = "无敌玩家"
BANGER = "好活当赏"
CHARGES = "强化普攻次数"

RULES = [
    {
        "on": "SKILL_CAST",
        "id": "p1506_skill_grants_laughs",
        "when": ["actor == self", "from_skill_id == 2.0"],
        "do": [{"op": "GAIN_RESOURCE", "resource": LAUGH, "amount": 5}],
        "source": "1506 银狼LV.999 战技 Shoot属性大爆发 (150602): 「获得 5 个笑点，对敌方全体造成等同于 160% 攻击力的虚数属性伤害」"
                  "—— #2 = 5（全等级固定）。",
        "note": "⭐ 数值 `5` 取自**数据行**（`skills.json` 1506 槽位 2 的 `param_list` 全等级为 `[0.8,5]`…`[2,5]`）✓ —— "
                "⚠ 这是**数据驱动**、不是我推的 ✓。⚠ 伤害那一半由技能行自己做 ✓，本条**只写获得笑点** ✓。",
    },
    {
        "on": "CAST_SETUP",
        "id": "p1506_elation_skill_grants_hidden",
        "when": ["actor == self", "from_skill_id == 20.0"],
        "do": [{"op": "GAIN_RESOURCE", "resource": HIDDEN, "amount": 15}],
        "source": "1506 银狼LV.999 欢愉技 殿堂级操作回放 (150620): 「获得 15 点【隐藏分】」—— 全等级固定 15。",
        "note": "⭐ 欢愉技的**施放事件**沿用既有写法 ✓（`CAST_SETUP` ＋ 条件 ✓，与 1503 的 `elation_skill_arms_*` 同形 ✓）✓；"
                "⚠ 但这里用 **`from_skill_id == 20`** ✓ 而不是 `from_category` ✓ —— 因为**收益只属于这一个技能** ✓，"
                "而分类会连槽位 21 一起命中 ✓。",
    },
    {
        "on": "CAST_SETUP",
        "id": "p1506_ult_enters_godmode",
        "when": ["actor == self", "from_category ULTRA"],
        "do": [
            {"op": "APPLY_BUFF", "buff": GOD, "permanent": True, "target": "self"},
            {"op": "GAIN_RESOURCE", "resource": CHARGES, "amount": 3, "to_max": True},
            {"op": "GAIN_RESOURCE", "resource": HIDDEN, "amount": 20},
        ],
        "source": "1506 银狼LV.999 终结技 无敌玩家，启动！ (150603): 「进入【无敌玩家】状态并且行动提前 100%」"
                  "＋ 行迹 隐藏关卡全成就 (1506103): 「进入【无敌玩家】状态后，获得 20 点【隐藏分】」",
        "note": "⭐ **【无敌玩家】就是让这个角色成为她自己的那个状态** ✓ —— 本会话之前它**只是被登记**（「状态若无任何规则读它就没有读者」）✓，"
                "而现在它有读者了 ✓：天赋的强化普攻计数 ✓、退出时清空隐藏分 ✓、星魂 2 ✓ ⇒ **读者数过线，可以建** ✓。"
                "⚠ 行迹 (1506103) 与本条**同轮出货** ✓ —— 它挂在**状态的施加点**上 ✓，而那个点就是这里 ✓。"
                "⚠ `to_max` 把强化普攻次数补满到 3 ✓（与 1503 的充能同一个词汇 ✓）✓。",
    },
    {
        "on": "STATE_ENDED",
        "id": "p1506_leaving_godmode_clears_hidden",
        "when": ["actor state_ended 无敌玩家"],
        "do": [{"op": "SPEND_RESOURCE", "resource": HIDDEN, "spendAll": True, "target": "self"}],
        "source": "1506 银狼LV.999 天赋: 「退出【无敌玩家】状态时【隐藏分】会被清空」",
        "note": "⭐ 用**状态结束事件** ✓（`STATE_ENDED` ＋ `actor state_ended <名>` ✓，本会话早先已出货 ✓）。"
                "⚠ 星魂 1 把这一句**改成保留 20%** ✓ ⇒ 那一条是**另一档**（`min_eidolon: 1` ✓），"
                "⚠ 且必须让**基础那条不再整份清空** ✓ —— 见 `p1506_e1_keeps_a_fifth_of_hidden` ✓。",
    },
    {
        "on": "STATE_ENDED",
        "id": "p1506_e1_keeps_a_fifth_of_hidden",
        "min_eidolon": 1,
        "when": ["actor state_ended 无敌玩家"],
        "do": [
            {"op": "SPEND_RESOURCE", "resource": HIDDEN, "spendAll": True, "target": "self"},
            {"op": "GAIN_RESOURCE", "resource": HIDDEN, "amountFromPrevious": True, "amountPercent": 0.2, "target": "self"},
        ],
        "source": "1506 银狼LV.999 星魂 1 以太编辑：星魂+1: 「退出【无敌玩家】状态时不会清空【隐藏分】，改为保留 20% 的【隐藏分】」"
                  "—— #2 = 0.2。",
        "note": "⚠⚠ **两条规则都在 `STATE_ENDED` 上，而基础那条没有星魂门** ✓ ⇒ 有星魂 1 时**两条都会跑** ✓。"
                "⚠ 解决办法就是**利用文件顺序** ✓：那条（先）清空 ✓、本条（后）按**事件携带的量**恢复 20% ✓。"
                "⚠ 所以本条的**顺序是承重的** ✓ —— 它必须排在 `p1506_leaving_godmode_clears_hidden` **之后** ✓，"
                "而且它先清再补 ✓（否则会变成保留 120% ✓）。⭐ 这一点由判据**同时读两条路径**钉住 ✓。",
    },
    {
        "on": "ULT_CAST",
        "id": "p1506_ult_installs_the_enhanced_basic",
        "when": ["actor == self"],
        "do": [{"op": "REPLACE_SKILL", "skill": "COMMON", "skill_id": 8, "turns": 3, "target": "self"}],
        "source": "1506 银狼LV.999 天赋: 「【无敌玩家】状态下…拥有强化普攻和强化欢愉技」—— 强化普攻是槽位 8。",
        "note": "⚠ 这一条是补的：没有它，【无敌玩家】装不上强化普攻，槽 8 的消耗与退出规则永远不会触发（症状是沉默的）。",
    },
    {
        "on": "BASIC_ATTACK",
        "id": "p1506_godmode_basic_spends_a_charge",
        "when": ["actor == self", "from_skill_id == 8.0"],
        "do": [{"op": "REMOVE_RESOURCE", "resource": CHARGES, "amount": 1, "target": "self"}],
        "source": "1506 银狼LV.999 天赋: 「完整施放 3 次强化普攻后退出【无敌玩家】状态」—— #5 = 3。",
        "note": "⚠ 用**夹取**（`REMOVE_RESOURCE`）而不是严格花费 ✓ —— 与 1503 的充能同一个理由 ✓："
                "计数是**状态的时长** ✓，不是**施放强化普攻的门票** ✓。⚠ 挂在 `BASIC_ATTACK` ✓（**施放事件** ✓）✓。",
    },
    {
        "on": "ATTACK_FINISHED",
        "id": "p1506_three_enhanced_basics_end_godmode",
        "when": ["actor == self", "self_resource:强化普攻次数 <= 0", "self has_state 无敌玩家"],
        "do": [{"op": "REMOVE_STATE", "buff": GOD, "target": "self"}],
        "source": "1506 银狼LV.999 天赋: 「完整施放 3 次强化普攻后退出【无敌玩家】状态」",
        "note": "⭐ 「**施放完 3 次之后**」的落点是 `ATTACK_FINISHED` ✓（攻击结算完 ✓），"
                "⚠ 与 1503 的 `deep_study_ends_when_the_charges_run_out` **同一个形状** ✓ —— 计数归零 ＋ 行动结束 ⇒ 状态结束 ✓。"
                "⚠ **退出本身**由 `REMOVE_STATE` 触发 ✓，而清空隐藏分**不在这里** ✓：它挂在 `STATE_ENDED` 上 ✓ ⇒ "
                "⭐ **一条路径、两处读者** ✓，星魂 1 的「保留 20%」因此**不需要在这条里再写一遍** ✓。",
    },
    {
        "on": "DEALING_DAMAGE",
        "id": "p1506_banger_basic_rider",
        "when": ["actor == self", "self has_state 好活当赏", "from_skill_id == 1.0"],
        "do": [{"op": "ADD_DAMAGE", "scale": "self_attr:ATTACK", "percent": 0.4, "element": "Imaginary",
                "damage_type": "ELATION"}],
        "source": "1506 银狼LV.999 天赋: 「持有【好活当赏】时，施放普攻、战技对受到攻击的敌方目标造成 40% 的虚数属性欢愉伤害」"
                  "—— #3 = 0.4。",
        "note": "⭐ 40% **攻击力**的**虚数欢愉**伤害 ✓（`scale self_attr:ATTACK` ✓、`element Imaginary` ✓、"
                "`damage_type ELATION` ✓）。⚠ 基数用 **ATK** ✓ 而不是欢愉基础值 ✓，因为原文写的是「**等同于 40%**」"
                "（承接同页普攻的「等同于 100% **攻击力**」）✓。⚠ 三个读数都由判据分别钉住 ✓。",
    },
    {
        "on": "DEALING_DAMAGE",
        "id": "p1506_banger_skill_rider",
        "when": ["actor == self", "self has_state 好活当赏", "from_skill_id == 2.0"],
        "do": [{"op": "ADD_DAMAGE", "scale": "self_attr:ATTACK", "percent": 0.4, "element": "Imaginary",
                "damage_type": "ELATION"}],
        "source": "1506 银狼LV.999 天赋: 「持有【好活当赏】时，施放普攻、战技对受到攻击的敌方目标造成 40% 的虚数属性欢愉伤害」"
                  "—— 同一句的另一个槽位（战技 2）。",
        "note": "⚠ **按槽拆成两条** ✓，与条件 DSL 无「或」的既有做法一致 ✓（1503 的形态 A／B 同样如此 ✓）。"
                "⚠ 两条的**数值完全相同** ✓ ⇒ 判据必须**分别**读两个槽位 ✓，否则删掉任意一条都不会被发现 ✓。",
    },
    {
        "on": "CAST_SETUP",
        "id": "p1506_trace_true_ending_at_20_laughs",
        "when": ["actor == self", "from_category ElationDamage", "counted_laughs >= 20"],
        "do": [{"op": "GAIN_RESOURCE", "resource": HIDDEN, "amount": 20}],
        "source": "1506 银狼LV.999 行迹 真结局解锁条件 (1506102): 「若施放欢愉技计入的笑点大于等于 20 个，额外获得 20 点【隐藏分】"
                  "（若大于等于 40 个，再额外获得 20 点）」",
        "note": "⭐⭐ 这一条是**新读取者 `counted_laughs` 的建库理由** ✓ —— 原文说的是「**计入的**笑点」✓，"
                "⚠ **不是**池子的当前值 ✓：两者在**池子自【好活当赏】到手后又涨过**时**不相等** ✓，"
                "而那正是那个状态存在的意义 ✓（本会话刚为此更正过引擎 ✓）。"
                "⚠ 目标**不限于槽位 20／21** ✓ —— 原文说的是「施放**欢愉技**」✓ ⇒ 用**分类** ✓、不用槽位 ✓（与上面那条相反 ✓）。",
    },
    {
        "on": "CAST_SETUP",
        "id": "p1506_trace_true_ending_at_40_laughs",
        "when": ["actor == self", "from_category ElationDamage", "counted_laughs >= 40"],
        "do": [{"op": "GAIN_RESOURCE", "resource": HIDDEN, "amount": 20}],
        "source": "1506 银狼LV.999 行迹 真结局解锁条件 (1506102): 「（若大于等于 40 个，再额外获得 20 点）」",
        "note": "⚠ 与 20 点那条**并存** ✓（40 点时两条都跑 ⇒ 共 40 点 ✓，与原文的「**再**额外」一致 ✓）。"
                "⚠ 两条**数值相同** ✓ ⇒ 判据必须**分别**用 20／40 两个门槛夹出来 ✓（39 点只给 20 ✓、40 点给 40 ✓）。",
    },
    {
        "on": "DEALING_DAMAGE",
        "id": "p1506_e4_demo_counts_five_times_the_laughs",
        "min_eidolon": 4,
        "when": ["actor == self", "from_skill_id == 21.0"],
        "do": [{"op": "ADD_DAMAGE", "scale": "counted_laughs", "percent": 5.0, "element": "Imaginary",
                "damage_type": "ELATION"}],
        "source": "1506 银狼LV.999 星魂 4 我来，我见，我..秒了: 「【崩坏级伤害演示】造成的欢愉伤害额外计入 5 倍的【笑点】」"
                  "—— #1 = 5。",
        "note": "⚠ 「**额外计入 5 倍的笑点**」读作**加算**（「再计入 5 倍那么多」✓），不是「把计入量乘 5」✓ —— "
                "⚠ 这是一个**读法选择** ✓，写在 note 里而不是埋着 ✓。"
                "⚠ `scale counted_laughs percent 5.0` ✓ 表达的就是「5 倍的笑点数」✓。",
    },
]


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored from", BACKUP)
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    before = len(data["rules"])

    if not any(r["id"] == LAUGH for r in data.get("resources", [])):
        data.setdefault("resources", []).append({
            "id": LAUGH,
            "max": 2147483647,
            "initial": 0,
            "scope": "PARTY",
            "source": "1506 银狼LV.999 战技／终结技: 「笑点为全队共享」⇒ 队伍级计数。",
        })
    if not any(r["id"] == CHARGES for r in data.get("resources", [])):
        data["resources"].append({
            "id": CHARGES,
            "max": 3,
            "initial": 0,
            "source": "1506 银狼LV.999 天赋: 「完整施放 3 次强化普攻后退出【无敌玩家】状态」—— 计数即时长。",
        })

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
    resources = {r["id"] for r in check.get("resources", [])}
    if missing or LAUGH not in resources or CHARGES not in resources:
        print("WRITE CHECK FAILED:", missing, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4

    # The two STATE_ENDED rules share an event: the ordering is load-bearing, so it is asserted rather than assumed.
    order = [r["id"] for r in check["rules"]]
    clear_at = order.index("p1506_leaving_godmode_clears_hidden")
    keep_at = order.index("p1506_e1_keeps_a_fifth_of_hidden")
    if not clear_at < keep_at:
        print(f"ORDER WRONG: clear@{clear_at} must precede keep@{keep_at} - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 5

    print(f"rules: {before} -> {len(check['rules'])}")
    print(f"resources: {sorted(resources)}")
    print(f"STATE_ENDED order OK: clears@{clear_at} < keeps@{keep_at}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
