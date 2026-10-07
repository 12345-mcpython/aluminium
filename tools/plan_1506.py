"""1506 (Silver Wolf LV.999): what can be written TODAY, and what each piece would need.

Reading the page (E:\\turnbasedgamedata\\aluminium_texts\\1506_银狼LV.999.md, 583 lines, 35 KB) against the 2 rules that
exist. This prints each clause, the value at Lv10, and my classification, so the plan is checkable rather than asserted.

Classifications:
  WRITE      expressible with the current op vocabulary
  NEEDS-X    blocked on a specific missing spelling (named in the row)
  APPROX     expressible only as an approximation (the project forbids these without registration)
"""
import json
import pathlib
import sys

OUT = pathlib.Path("tools/_1506_plan.txt")
CHARS = pathlib.Path("src/main/resources/characters/1506.json")

# clause, level-10 value, classification, the spelling it needs / why
PLAN = [
    ("普攻 拳头硬了！ (slot 1)", "100% ATK 虚数单体", "WRITE",
     "DAMAGE scale self_attr:ATTACK percent 1.0 — data-driven, no rule needed beyond the skill row"),
    ("战技 Shoot属性大爆发 (slot 2)", "获得 5 笑点 + 160% ATK 全体", "WRITE",
     "GAIN_RESOURCE 笑点 5 on SKILL_CAST + the skill row's own damage"),
    ("终结技 无敌玩家，启动！ (slot 3)", "行动提前 100%", "SHIPPED", "p1506_ult_advance"),
    ("终结技 · 无敌玩家状态与结界", "状态 + 结界内每消耗 1 战技点有概率触发盲盒", "NEEDS-ZONE",
     "a ZONE (region) mechanism and 战技点 consumption events — neither exists"),
    ("天赋 · 隐藏分来源", "获得笑点时获得等量隐藏分", "SHIPPED", "p1506_laughs_to_hidden"),
    ("天赋 · 每点隐藏分 +0.4% 暴击率", "0.4% per point", "WRITE",
     "MODIFY_ATTR CRIT_CHANCE scale self_resource:隐藏分 percent 0.004, persistent while >0"),
    ("天赋 · 暴击率满 100% 后每点改 +0.8% 暴击伤害", "0.8% per point ABOVE the 100% cap", "NEEDS-CAP",
     "a bounded scaling: min(0.004 * hidden, 0.4) for the crit part and 0.008 * max(0, hidden - 100) for the rest. "
     "No spelling clamps a scale's result, so this cannot be written exactly"),
    ("天赋 · 无敌玩家状态下免疫控制、不能放终结技", "control immunity", "NEEDS-CONTROL",
     "the engine has no crowd-control immunity marker (the same gap the page's 仓库技 needs)"),
    ("天赋 · 完整施放 3 次强化普攻后退出", "3 uses", "WRITE",
     "a 充能-style counter + REMOVE_STATE, exactly what Pearl's 充能 just shipped"),
    ("天赋 · 退出时清空隐藏分", "clear on exit", "WRITE", "STATE_ENDED + RESET_RESOURCE_CAP / a clear op"),
    ("天赋 · 持好活当赏时普攻/战技附 40% 欢愉伤害", "40% ATK 虚数欢愉", "WRITE",
     "DEALING_DAMAGE + self has_state 好活当赏 + from_skill 1/2 + ADD_DAMAGE elation"),
    ("欢愉技 殿堂级操作回放 (slot 20)", "获得 15 点隐藏分", "WRITE",
     "GAIN_RESOURCE 隐藏分 15 on the elation cast event (the slot-20 family 1513/1505 already use)"),
    ("欢愉技·强化 崩坏级伤害演示 (slot 21)", "6 次 × 90% 欢愉伤害 + 重置盲盒概率", "WRITE(part)",
     "DAMAGE times 6, damage_type ELATION — the 固定概率 reset needs the 盲盒 state, which is ZONE-bound"),
    ("行迹 真结局解锁条件", "施放欢愉技计入笑点 >= 20 则 +20 隐藏分 (>= 40 则再 +20)", "WRITE",
     "the elation cast event carries the counted laughs; two threshold rules"),
    ("行迹 隐藏关卡全成就", "进入无敌玩家状态后 +20 隐藏分", "WRITE", "the state's application event"),
    ("行迹 假结局速通攻略", "速度 >= 160 则 +50% 欢愉度，之后每 1 点速度 +2%，最多计入 100 点", "NEEDS-CAP",
     "min(2 * (speed - 160), 200)% — again a clamped scale"),
    ("星魂 1", "结界中敌方受伤 +20%；退出时保留 20% 隐藏分", "NEEDS-ZONE + PARTIAL",
     "the retention half is writable (keep a share on exit); the zone half is not"),
    ("星魂 2", "进入时延长增益 1 回合；每 +120 隐藏分给 1 额外回合", "PARTIAL",
     "the every-120 threshold needs a cumulative counter (obtainable), the buff extension needs a BUFF_EXTEND op"),
    ("星魂 4", "崩坏级伤害演示额外计入 5 倍笑点", "WRITE", "an ADD_DAMAGE rider on slot 21"),
    ("星魂 6", "强化普攻期间欢愉伤害增笑 50%；敌方获禁限弱点", "PARTIAL",
     "the 增笑 half is writable (ELATION_DAMAGE_AMP while the state is up); the weakness half needs enemy-side writes"),
]


def main():
    data = json.loads(CHARS.read_text(encoding="utf-8"))
    shipped = [r["id"] for r in data["rules"]]
    lines = [f"shipped rules: {len(shipped)} -> {', '.join(shipped)}", ""]
    counts = {}
    for clause, value, kind, needs in PLAN:
        counts[kind] = counts.get(kind, 0) + 1
        lines.append(f"[{kind:14s}] {clause}")
        lines.append(f"                 值: {value}")
        lines.append(f"                 落法: {needs}")
    lines.append("")
    lines.append("summary: " + ", ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    writable = [row for row in PLAN if row[2] in ("WRITE", "SHIPPED")]
    lines.append(f"writable-or-shipped rows: {len(writable)} of {len(PLAN)}")

    text = "\n".join(lines)
    OUT.write_text(text, encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    sys.exit(main())
