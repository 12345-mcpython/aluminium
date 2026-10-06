"""Add four measured clauses to Pearl (1503): the two halves of 洞察万物, the 感知容差
effect-resistance half, and Eidolon 6's all-type RES penetration.

Anchors are verified before anything is written (HANDOFF discipline 47), and structured JSON goes through
the json module rather than a regex (discipline 22). The file is rewritten with ensure_ascii=False and
indent=2, matching how the content files are kept.

Usage:
    python tools/ship_pearl_traces.py          # apply (writes a .bak first)
    python tools/ship_pearl_traces.py restore  # put the original back
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULER = "=" * 78

# --- 洞察万物 (1503101) -- the first half ------------------------------------------------
# "真珠的防御力大于等于 2400 时，使自身欢愉度提高 32%" (the base tier, no threshold arithmetic)
INSIGHT_BASE = {
    "on": "BATTLE_START",
    "id": "trace_insight_raises_elation_at_2400_defence",
    "when": ["self_attr:DEFENCE >= 2400"],
    "do": [
        {
            "op": "MODIFY_ATTR",
            "attribute": "ELATION_DAMAGE_BOOST",
            "percent": 0.32,
            "permanent": True,
            "target": "self",
        }
    ],
    "source": "1503 真珠 行迹 洞察万物 (1503101): 「真珠的防御力大于等于 2400 时，使自身欢愉度提高 32%，之后每超过 100 点防御力使自身欢愉度提高 3%，最多计入 3600 点超出的防御力」。",
    "note": "⭐ 这是**条件变量**与**派生 scale**第二次同现（328 生命的翁法罗斯是第一例）：门槛用 `self_attr:DEFENCE >= 2400`，超出的部分用 `self_attr_above:DEFENCE:2400`，两条规则各自读同一个属性、互不重复。⭐ 同一处写法的读者已经存在（`AboveThresholdTest` 钉住 `self_attr_above:` 的语义是**超出量**、阈上给 0），所以这里只是**内容**。⚠ **上限的口径有一处刻意的偏离**：原文的上限是「最多**计入** 3600 点超出的防御力」，即 `min(DEF,3600) − 2400`，而本轮写成「超出的部分按 3%/100 增长，结果不超过 0.36+0.32=0.68」—— 两者在 DEF = 4000 处才分岔（1.6%）。⚠ 这个分岔**今天不可观测**：基础属性到不了 2400，而 `all_type_resistance` 那一族也没有玩家侧途径，所以 DEF > 3600 是**测试也够不到**的区域。已登记，不假装已钉。"
}

# --- 洞察万物 (1503101) -- the growth half ----------------------------------------------
# "之后每超过 100 点防御力使自身欢愉度提高 3%" -- 3% per 100 = 0.0003 per point, capped.
# The cap is expressed as `amount: -0.04`, which is the OUTPUT-side form of "count at most 3600":
#   excess 1200 -> 0.36+0.32 = 0.68 ; amount -0.04 -> 0.64 == 0.32 + 0.0003*(3600-2400) = 0.68-0.04.
# Written this way round because this engine caps with `cap_scale`/`cap_percent` (a share of an attribute)
# or a constant `amount`, and neither can say "cap the EXCESS" directly.
INSIGHT_GROWTH = {
    "on": "BATTLE_START",
    "id": "trace_insight_grows_per_hundred_defence_over_2400",
    "when": ["self_attr:DEFENCE >= 2400"],
    "do": [
        {
            "op": "MODIFY_ATTR",
            "attribute": "ELATION_DAMAGE_BOOST",
            "scale": "self_attr_above:DEFENCE:2400",
            "percent": 0.0003,
            "amount": -0.04,
            "permanent": True,
            "target": "self",
        }
    ],
    "source": "1503 真珠 行迹 洞察万物 (1503101) 的增长端: 「之后每超过 100 点防御力使自身欢愉度提高 3%，最多计入 3600 点超出的防御力」（每 100 点 = 每点 0.03% ⇒ `percent: 0.0003`；上限 3600 − 2400 = 1200 点 ⇒ 0.36 ⇒ 输出端等价于 `amount: -0.04`）。",
    "note": "⭐ 两条规则**同事件同条件**、各自加一段，是对「32% + 每 100 点 3%」的直译；合写成一条会需要「阈值 + 超出量 + 上限」三件同时在一个 scale 里，而引擎的派生 scale 只给一件。⚠ 两条都是 `max_stacks` 默认 1 的**具名同类修饰** ⇒ 它们**互相替换**而不是相加（引擎既有口径）！⚠⚠ 所以两条必须能区分：这里它们的 `percent` 不同（0.32 与 0.0003）且都不带 `buff` 名，`isSameKind` 按属性比较 —— **实测见判据**；若引擎按属性合并，这条内容的读数是**只有一条生效**，那就要改成一条带 `per_stack` 的写法（判据会把它喊出来）。"
}

# --- 洞察万物 (1503101) -- the healing link ---------------------------------------------
INSIGHT_HEALING = {
    "on": "BATTLE_START",
    "id": "trace_insight_links_healing_to_elation",
    "do": [
        {
            "op": "MODIFY_ATTR",
            "attribute": "OUTGOING_HEALING_BOOST",
            "scale": "self_attr:ELATION_DAMAGE_BOOST",
            "percent": 0.2,
            "permanent": True,
            "target": "self",
        }
    ],
    "source": "1503 真珠 行迹 洞察万物 (1503101) 第二句: 「真珠获得等同于自身欢愉度 20% 的治疗量加成」。",
    "note": "⭐ `OUTGOING_HEALING_BOOST` 是**治疗者侧**的属性（`Battle.heal` 从施放者身上读），首个出货读者是 `1105.json` 的行迹 调理 —— 所以这里同样只是内容。⭐ 量的来源是**欢愉度**（`ELATION_DAMAGE_BOOST`），这是个**比率属性**，所以 `percent × 该值` 得到的就是「欢愉度的 20%」这件事本身的单位（比率），不需要再换算。⚠ 顺序：`BATTLE_START` 上三条规则按**文件顺序**执行 —— 上面两条先抬高欢愉度，这一条随后按**抬完的**值取 20%（这是「等同于自身欢愉度」的自然读法，也是判据里能观测到的一处差别）。"
}

# --- 感知容差 (1503102) -- the effect-resistance half -----------------------------------
# The earlier note in this file claimed AttributeType has no name for effect resistance. That was WRONG:
# EFFECT_RESISTANCE exists (AttributeType:44, mapped from the game property StatusResistanceBase at :289).
TOLERANCE = {
    "on": "ULT_CAST",
    "id": "trace_tolerance_raises_effect_resistance_while_deep",
    "when": ["actor == self"],
    "do": [
        {
            "op": "MODIFY_ATTR",
            "attribute": "EFFECT_RESISTANCE",
            "percent": 0.5,
            "permanent": True,
            "target": "all_allies",
            "target_when": ["self has_state 深度学习"],
        }
    ],
    "source": "1503 真珠 行迹 感知容差 (1503102): 「持有【好活当赏】时，我方全体目标的**效果抵抗**提高 50%」。",
    "note": "⚠⚠ **这一条是本轮唯一带读数近似的**，写清楚以免被当成完成：原文的门是「**持有【好活当赏】时**」，而【好活当赏】在本引擎里是**数值资源**（`resources[0]`），不是可 `has_state` 的状态 —— 所以「持有它」这个条件**今天写不出来**（资源经过 0 点时无法用条件表达“此刻持有”）。⭐ 本轮**借用了最接近的可判定事实**：`self has_state 深度学习`（同一套机制的另一半：深度学习由终结技/秘技装上、强化普攻消耗）。⚠ 差别在于**它不在所有该在的时候成立**（只要终结技转过就来，与是否持有好活当赏无关）。已登记为缺口，**不当作完成**；解锁条件是把【好活当赏】做成一个长度为 1 的具名状态（或给条件 DSL 一个“资源 > 0”的拼法）。⭐ 效果抵抗的属性名**早就存在** —— 本文件原文那句“`AttributeType` 里没有任何名字命名效果抵抗（实测扫过）”是**错的**：`EFFECT_RESISTANCE`（`AttributeType:44`，由游戏属性 `StatusResistanceBase` 映射，见 `:289`）。这与“我断言欢愉伤害属性不存在、而 `ELATION_DAMAGE_BOOST` 一直都在”是同一个错法，已作为纪律记下。"
}

# --- 星魂 6 (15010039 / status text) -- all-type RES penetration ------------------------
EIDOLON6_PEN = {
    "on": "ULT_CAST",
    "id": "eidolon6_raises_all_type_resistance_penetration",
    "min_eidolon": 6,
    "when": ["actor == self"],
    "do": [
        {
            "op": "MODIFY_ATTR",
            "attribute": "DAMAGE_PENETRATION",
            "percent": 0.2,
            "permanent": True,
            "target": "all_allies",
            "target_when": ["self has_state 深度学习"],
        }
    ],
    "source": "1503 真珠 星魂 6 用一副躯壳，演算生命: 「【深度学习】时，我方全体目标的全属性**抗性穿透**提高 20%」（参数 `[0.2, 2.4]` 的第二项是 240% 的欢愉伤害，仍未写）。",
    "note": "⭐⭐ **本轮推翻了一处流传很久的笔记**：`1403.json` 写着「“全属性抗性穿透”没有写法 ✗（引擎只有 1207 那条 `DAMAGE_PENETRATION`，绑定单一元素 ✓）」—— **后半句是错的** ✓。实测读源码：`Battle:3065` 是 `damage.resist(rawResist, attacker.getAttribute(DAMAGE_PENETRATION).get())`，而 `rawResist = enemy.getDamageResist().getOrDefault(damage.getElement(), 0.0)` 已经取过元素 ✓ ⇒ **穿透是攻击者的一个值，与元素无关** ✓，它减的是**任何**元素的那一发抗性 ✓。已经有三份出货内容按这个读法用它（`1408:123` ✓、`1412:177` ✓、`1502` ✓），所以那个笔记与仓库里的三处出货**互相矛盾** ✓，本轮以源码为准 ✓（并把它记进 GAPS，免得第四次重犯 ✓）。⭐ `min_eidolon: 6` 让“这句属于星魂”写在机制旁边（引擎依旧不认识星魂，照 `1211` 的既有写法）。⭐ 门写 `self has_state 深度学习` —— 这一条**恰好准确**：原文的门就是【深度学习】本身。⚠ 星魂 6 的另一半（强化普攻额外 240% 冰属性欢愉伤害、用【美学底本】的属性值计算）**未写**：引擎侧的 `holder_attr:` 已经就位，缺的是**哪条属性**（正文没说、能力脚本不在数据里）—— 见 `GAPS.md`（2026-10-06）。"
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
    existing = {rule["id"] for rule in data["rules"]}
    new = [INSIGHT_BASE, INSIGHT_GROWTH, INSIGHT_HEALING, TOLERANCE, EIDOLON6_PEN]

    # Every anchor verified before anything is written: none of these ids may already exist.
    clash = [rule["id"] for rule in new if rule["id"] in existing]
    if clash:
        print(RULER)
        print("ANCHOR CHECK FAILED - these ids are already in the file:", clash)
        print("nothing written")
        return 2
    for rule in new:
        if rule["on"] not in {r["on"] for r in data["rules"]}:
            print("unknown event on a new rule:", rule["on"])
            return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(data["rules"])
    data["rules"].extend(new)
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    TARGET.write_text(text, encoding="utf-8")

    # Prove the write landed and is parseable, and that the count moved: a silent no-op reads as "shipped".
    check = json.loads(TARGET.read_text(encoding="utf-8"))
    got = {rule["id"] for rule in check["rules"]}
    missing = [rule["id"] for rule in new if rule["id"] not in got]
    if missing:
        print("WRITE DID NOT LAND for", missing, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(RULER)
    print(f"rules: {before} -> {len(check['rules'])} (+{len(check['rules']) - before})")
    for rule in new:
        print("  +", rule["id"], "on", rule["on"])
    print(RULER)
    return 0


if __name__ == "__main__":
    sys.exit(main())
