"""1506 (Silver Wolf LV.999): what is still missing, clause by clause, measured against the page.

Reads her page (E:\\turnbasedgamedata\\aluminium_texts\\1506_银狼LV.999.md) and her shipped rules, and reports every clause
with its Lv10 value and whether a rule covers it - so "what is left" is a list, not an impression.
"""
import json
import pathlib
import sys

PAGE = pathlib.Path(r"E:\turnbasedgamedata\aluminium_texts\1506_银狼LV.999.md")
CHARS = pathlib.Path("src/main/resources/characters/1506.json")
OUT = pathlib.Path("tools/_1506_remaining.txt")

# clause, Lv10 value, status, what it needs if not shipped
CLAUSES = [
    ("仓库技 999安全卫士", "敌方施加控制类负面时全体获【防火墙】1回合，每波1次", "MISSING",
     "引擎没有「控制类负面状态」标记，也没有「波次开始」事件"),
    ("普攻 拳头硬了！ (槽1)", "100% ATK 虚数单体", "WRITE",
     "数据行自带，无需规则（skills.json 槽1 param_list 到 1.4）"),
    ("战技 (槽2)", "5 笑点 + 160% ATK 全体", "SHIPPED", "p1506_skill_grants_laughs（伤害由数据行做）"),
    ("终结技 (槽3) 行动提前 100%", "—", "SHIPPED", "p1506_ult_advance"),
    ("终结技 进入【无敌玩家】", "—", "SHIPPED", "p1506_ult_enters_godmode"),
    ("终结技 结界 + 每消耗1战技点触发盲盒", "90% 虚数欢愉伤害，全体均分", "MISSING",
     "需区域（结界）机制 + 战技点消耗事件；两者都没有"),
    ("终结技 盲盒三选一", "真实伤害20%／恢复2战技点／3笑点", "MISSING", "同上（盲盒本身没有触发点）"),
    ("终结技 固定概率 100% → 每次降至20%", "0.2", "MISSING", "需「固定概率」状态 + 每次成功后的衰减；引擎无此概念"),
    ("天赋 获得笑点 → 等量隐藏分", "—", "SHIPPED", "p1506_laughs_to_hidden"),
    ("天赋 每点隐藏分 +0.4% 暴击率", "0.004/点", "MISSING",
     "⚠ 与下一条是同一句的两半，且「暴击率达到100%后改加暴伤」需要一个在阈值处夹住的缩放"),
    ("天赋 满100%后每点改 +0.8% 暴击伤害", "0.008/点", "MISSING", "同上：需封顶缩放（scale 的结果无法夹）"),
    ("天赋 无敌玩家期间免疫控制", "—", "MISSING", "引擎没有控制标记"),
    ("天赋 无敌玩家期间无法施放终结技", "—", "MISSING", "需「禁止施放终结技」的机制；引擎无此禁用词"),
    ("天赋 拥有强化普攻/强化欢愉技", "—", "SHIPPED", "p1506_ult_installs_the_enhanced_basic（槽8）"),
    ("天赋 完整3次强化普攻打完退出", "3", "SHIPPED", "p1506_godmode_basic_spends_a_charge + ..._end_godmode"),
    ("天赋 退出时清空隐藏分", "—", "SHIPPED", "p1506_leaving_godmode_clears_hidden"),
    ("天赋 持好活当赏时普攻/战技 40% 欢愉伤害", "0.4", "SHIPPED(判据未过)",
     "p1506_banger_basic_rider + ..._skill_rider —— ⚠ 判据未通过，需先分清是夹具还是规则"),
    ("天赋 强化普攻的技能伤害改为欢愉伤害", "—", "MISSING",
     "需把槽8技能行的伤害类型整体换成 ELATION；规则无法改数据行的 damage_type"),
    ("秘技 召唤【怪怪怪味豆】", "—", "MISSING", "需召唤/秘技层机制（与她已有的 summon 家族不同）"),
    ("秘技 每波开始触发1次盲盒，固定计入 99 点好活当赏", "99", "MISSING", "需「波次开始」事件 + 盲盒本身"),
    ("强化普攻 (槽8) 240% ATK 分100段弹射", "2.4", "PARTIAL",
     "数据行有倍率；⚠「每弹射一定段数暂停并触发盲盒」需盲盒；⚠「对全体致命则给额外回合并按剩余段数重放」需弹射段数追踪"),
    ("强化普攻 每60点隐藏分伤害+15%，最多2层", "0.15/层，上限2", "MISSING",
     "⚠ 需「每 60 点」的累计阈值 + 层数上限 ⇒ 与封顶缩放同族"),
    ("强化普攻 每回合首次触发时所有增益延长1回合", "—", "MISSING", "需 BUFF_EXTEND（延长身上所有增益）"),
    ("强化普攻 最后一击 100% ATK 全体均分", "1.0", "MISSING", "⚠ 需「全部结算后发动最后一击」的收尾阶段"),
    ("欢愉技 殿堂级操作回放 (槽20) +15 隐藏分", "15", "SHIPPED", "p1506_elation_skill_grants_hidden"),
    ("欢愉技·强化 崩坏级伤害演示 (槽21) 6×90% 欢愉", "0.9×6", "PARTIAL",
     "伤害可由数据行做；⚠「重置盲盒固定概率」需盲盒"),
    ("行迹 真结局解锁条件 20/40 笑点 → +20/+20", "20/20", "SHIPPED", "两条 CAST_SETUP 门槛"),
    ("行迹 隐藏关卡全成就 进入无敌玩家 +20", "20", "SHIPPED", "并入 p1506_ult_enters_godmode"),
    ("行迹 假结局速通攻略 速度≥160 欢愉度+50%，每点+2%，最多100点", "0.5 + 上限2.0", "MISSING",
     "⚠ 需封顶缩放（min(2*(speed-160), 200)%）"),
    ("星魂 1 结界内敌方受伤+20%", "0.2", "MISSING", "需结界"),
    ("星魂 1 退出时保留20%隐藏分", "0.2", "SHIPPED(判据未过)",
     "p1506_e1_keeps_a_fifth_of_hidden —— ⚠ 读到 0 而非 12，见 GAPS"),
    ("星魂 2 进入时所有增益延长1回合", "—", "MISSING", "需 BUFF_EXTEND"),
    ("星魂 2 每+120隐藏分给1额外回合并恢复1次强化普攻", "120", "MISSING",
     "⚠ 需「本次状态下累计提高量」的计数 + 每达阈值一次"),
    ("星魂 3/5 技能等级 +N", "见表", "PARTIAL", "等级加成由星魂表的 RAISE_SKILL_LEVEL 家族做；需核对是否已覆盖"),
    ("星魂 4 崩坏级演示额外计入5倍笑点", "5", "SHIPPED", "p1506_e4_demo_counts_five_times_the_laughs"),
    ("星魂 6 强化普攻期间欢愉伤害增笑50%", "0.5", "WRITEable",
     "可用 ELATION_DAMAGE_AMP 在【无敌玩家】期间；⚠ 原文说的是「增笑」，需确认映射"),
    ("星魂 6 敌方获【禁限弱点】全属性弱点+基础抗性降0或20%", "0.2", "MISSING", "需对敌方写入弱点与抗性；引擎无此写入"),
]


def main():
    data = json.loads(CHARS.read_text(encoding="utf-8"))
    rules = [r["id"] for r in data["rules"]]
    lines = [f"shipped rules: {len(rules)}"]
    for rid in rules:
        lines.append(f"    {rid}")
    lines.append("")
    counts = {}
    shipped_clean, shipped_open, missing, partial = [], [], [], []
    for clause, value, status, needs in CLAUSES:
        counts[status] = counts.get(status, 0) + 1
        if status == "SHIPPED":
            shipped_clean.append(clause)
        elif status.startswith("SHIPPED("):
            shipped_open.append(clause)
        elif status == "PARTIAL":
            partial.append(clause)
        else:
            missing.append(clause)
        lines.append(f"[{status:16s}] {clause}")
        lines.append(f"                     值: {value}")
        lines.append(f"                     落法: {needs}")
    lines.append("")
    lines.append(f"clauses: {len(CLAUSES)}  ->  " + "  ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    lines.append("")
    lines.append(f"SHIPPED and passing      : {len(shipped_clean)}")
    lines.append(f"SHIPPED but judge failing: {len(shipped_open)}  -> {shipped_open}")
    lines.append(f"PARTIAL                  : {len(partial)}  -> {partial}")
    lines.append(f"NOT shipped              : {len(missing)}")
    for row in missing:
        lines.append(f"    - {row}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    sys.exit(main())
