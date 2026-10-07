"""1506's remaining work, corrected for clauses that need NO rule at all.

The first pass marked "普攻 槽1" and "强化普攻 最后一击" as missing, which overstated the gap: those are DAMAGE, and damage
comes from the skill's own data row (skills.json slot 1 = 1.4 at Lv10, slot 8 = 3.36). A rule is only needed for the
EFFECTS around the damage.
"""
import json
import pathlib

DATA = pathlib.Path("src/main/resources/data")
OUT = pathlib.Path("tools/_1506_gaps_corrected.txt")

# The bars the project uses for a new ENGINE spelling: at least two readers, or a document that states it plainly.
BUCKETS = {
    "已出货且判据通过": [
        "战技 5 笑点", "终结技 行动提前 100%", "终结技 进入【无敌玩家】",
        "天赋 笑点 → 等量隐藏分", "天赋 拥有强化普攻", "天赋 3 次强化普攻后退出",
        "天赋 退出时清空隐藏分", "行迹 真结局 20 笑点 → +20", "行迹 真结局 40 笑点 → +20",
        "行迹 隐藏关卡全成就 进入无敌玩家 +20", "星魂 4 五倍笑点",
    ],
    "已出货但判据未通过": [
        "星魂 1 退出时保留 20% 隐藏分 —— 读到 0（应 12）；下一步量 previousCredited",
        "天赋 持【好活当赏】时普攻 40% 欢愉伤害 —— 与无状态伤害相同；先分清夹具还是规则",
        "天赋 持【好活当赏】时战技 40% 欢愉伤害（与上一条同源）",
    ],
    "不需要规则（数据行自带）": [
        "普攻 槽1 100%→140% ATK 虚数单体（skills.json 槽1 param_list L10 = 1.4）",
        "强化普攻 槽8 240%→336% ATK 分 100 段弹射（槽8 L10 = [3.36, 0, 100, 1.4, 3, 60, 2, 0.15]）",
        "欢愉技·强化 槽21 6 × 90% 欢愉伤害（槽21 L10 = [0.9, 6]）",
    ],
    "可写，还没有写": [
        "星魂 3 战技+2 / 普攻+1 / 欢愉技+1（eidolons.json 1506 E3 skill_add_level 有 8 项）",
        "星魂 5 终结技+2 / 天赋+2 / 欢愉技+1（E5 skill_add_level 有 4 项）",
        "星魂 6 强化普攻期间欢愉伤害增笑 50%（ELATION_DAMAGE_AMP，与 1503 的增笑同一个词汇）",
    ],
    "卡在同一种缺失机制上": {
        "封顶缩放（scale 的结果无法在阈值处夹住）": [
            "天赋 每点隐藏分 +0.4% 暴击率，满 100% 后每点改 +0.8% 暴击伤害",
            "行迹 假结局速通攻略 速度≥160 欢愉度+50%，每点+2%，最多计入 100 点",
            "强化普攻 每 60 点隐藏分伤害 +15%，最多 2 层",
        ],
        "区域／结界 + 战技点消耗事件": [
            "终结技 结界内每消耗 1 战技点触发【头号补给盲盒】",
            "终结技 盲盒三选一（真实伤害 20%／恢复 2 战技点／3 笑点）",
            "终结技 固定概率 100% → 每次成功降至 20%",
            "秘技 每波开始触发 1 次盲盒，固定计入 99 点好活当赏",
            "星魂 1 结界内敌方受伤 +20%",
        ],
        "控制类负面状态的标记": [
            "仓库技 999安全卫士（敌方施加控制 → 全体【防火墙】1 回合，每波 1 次）",
            "天赋 无敌玩家期间免疫控制",
        ],
        "「禁止施放终结技」的禁用词": [
            "天赋 无敌玩家期间无法施放终结技",
        ],
        "BUFF_EXTEND（延长身上所有增益）": [
            "强化普攻 每回合首次触发时所有增益延长 1 回合",
            "星魂 2 进入【无敌玩家】时所有增益延长 1 回合",
        ],
        "累计阈值计数（每达 N 点触发一次）": [
            "星魂 2 每 +120 隐藏分给 1 额外回合并恢复 1 次强化普攻",
        ],
        "对敌方写入弱点／抗性": [
            "星魂 6 敌方获【禁限弱点】全属性弱点、基础抗性降 0（为 0 则降 20%）",
        ],
        "把数据行的伤害类型整体改写": [
            "天赋 强化普攻的技能伤害改为欢愉伤害",
        ],
        "收尾阶段（全部结算后发动最后一击）": [
            "强化普攻 最后一击 100% ATK 全体均分",
        ],
        "召唤／秘技层机制": [
            "秘技 召唤【怪怪怪味豆】",
        ],
    },
}


def main():
    chars = json.loads((pathlib.Path("src/main/resources/characters/1506.json")).read_text(encoding="utf-8"))
    lines = [f"her rules in the file: {len(chars['rules'])}", ""]
    for bucket, rows in BUCKETS.items():
        if isinstance(rows, dict):
            lines.append(f"== {bucket} ==")
            for why, clauses in rows.items():
                lines.append(f"   -- {why} ({len(clauses)}) --")
                for clause in clauses:
                    lines.append(f"      {clause}")
        else:
            lines.append(f"== {bucket} ({len(rows)}) ==")
            for clause in rows:
                lines.append(f"   {clause}")
        lines.append("")

    counted = sum(len(r) for r in BUCKETS.values() if not isinstance(r, dict))
    blocked = sum(len(v) for r in BUCKETS.values() if isinstance(r, dict) for v in r.values())
    lines.append(f"shipped-passing {len(BUCKETS['已出货且判据通过'])} ; "
                 f"shipped-open {len(BUCKETS['已出货但判据未通过'])} ; "
                 f"needs-no-rule {len(BUCKETS['不需要规则（数据行自带）'])} ; "
                 f"writable-now {len(BUCKETS['可写，还没有写'])} ; blocked {blocked}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
