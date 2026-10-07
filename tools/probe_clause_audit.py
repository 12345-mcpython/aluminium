"""Line the character page up against what the project actually ships, clause by clause.

The page (docs 1503_真珠.md) is the complete list of her sentences. This prints every clause that names a number or a
mechanic next to the rules in her file that mention it, so "what is still missing" is a list produced by comparison
rather than by reading both and remembering.

Output goes to tools/_pearl_clause_audit.txt.
"""
import json
import pathlib
import re

OUT = pathlib.Path("tools/_pearl_clause_audit.txt")
TARGET = pathlib.Path("src/main/resources/characters/1503.json")

# (label, the clause as the page states it, keywords that identify the shipped rule(s) for it)
CLAUSES = [
    ("普攻", "对指定敌方单体造成等同于真珠 90% 防御力的冰属性伤害", ["150301", "basic"]),
    ("战技", "获得15点【好活当赏】，为我方全体目标回复…12% 防御力+240…额外为最低者再回复一次",
     ["skill_heals_everyone"]),
    ("终结技-充能", "【深度学习】具有3点充能，施放强化普攻后消耗1点，行动后若无充能则结束",
     ["charge", "充能", "deep_study"]),
    ("终结技-底本", "【深度学习】除自身外的指定我方单体，使目标成为【美学底本】",
     ["ult_marks_the_archetype"]),
    ("终结技-提前", "1/2/3 个欢愉角色 ⇒ 底本行动提前 10%/15%/30%", ["ult_advances_the_target"]),
    ("终结技-额外回合", "4 个或以上 ⇒ 底本获得1个额外回合", ["ult_grants_an_extra_turn"]),
    ("终结技-回合奖励", "该额外回合开始时获得30点好活当赏与60点笑点，结束时移除",
     ["extra_turn_pays", "extra_turn_takes"]),
    ("终结技-强化普攻", "【深度学习】时普攻强化为【行笔，绘制末浪】；底本为欢愉则强化为【行笔，幻造星月】",
     ["enhanced_basic_is_form"]),
    ("终结技-额外欢愉伤害", "攻击后额外造成60%的冰属性欢愉伤害，按【美学底本】属性值计算",
     ["form_a_extra", "eidolon6_extra"]),
    ("天赋-抵御值", "每点好活当赏=200抵御值，可消耗为我方目标抵挡60%的伤害", ["defence_value"]),
    ("天赋-上限", "真珠的好活当赏持续时间无限，持有上限50点", []),
    ("天赋-减伤", "我方目标血量≤50%时受到的伤害降低30%", ["talent_reduces"]),
    ("秘技", "使用秘技后获得【美学底本】，切换行动角色时转移至当前行动角色",
     ["technique"]),
    ("秘技-开战", "下一次战斗开始时真珠获得20点好活当赏，并【深度学习】持有底本的角色，2点充能",
     ["technique_grants"]),
    ("强化普攻-伤害", "对敌方全体造成等同于真珠100%防御力的冰属性伤害", ["enhanced_basic_heals"]),
    ("强化普攻-治疗", "为我方全体回复8%防御力+160，额外为最低者再回复一次", ["enhanced_basic_heals"]),
    ("强化普攻-欢愉伤害", "持有好活当赏时额外造成15%的冰属性欢愉伤害", ["elation", "form_a_extra"]),
    ("欢愉技", "1/2/3/4 个欢愉角色 ⇒ 下一次攻击后额外造成10%/15%/20%/40%对应属性欢愉伤害",
     ["elation_skill"]),
    ("行迹-洞察万物", "防御≥2400⇒欢愉度+32%，每超100防御再+3%，最多计3600；获得自身欢愉度20%的治疗加成",
     ["trace_insight"]),
    ("行迹-感知容差-抵抗", "持有好活当赏时，我方全体效果抵抗+50%", ["trace_tolerance"]),
    ("行迹-感知容差-获得", "我方目标回合开始时，真珠获得5点好活当赏，最多获得50点，真珠回合开始时重置",
     ["trace_gains_on_an_ally_turn_start", "reset"]),
    ("行迹-感知容差-解除", "施放强化普攻和战技时，解除我方全体目标的1个负面效果", ["DISPEL", "dispel"]),
    ("行迹-艺术壁垒", "进入战斗和施放终结技后，若底本为欢愉，使其下一次施放终结技时为真珠恢复90点能量",
     ["art_barrier"]),
    ("行迹-属性行", "防御5%/7.5%/10%、速度2/3/4、效果抵抗4%/6%、欢愉度4%/6%",
     ["stat_traces"]),
    ("星魂1", "2/3/4 个欢愉角色 ⇒ 全体欢愉度+10%/20%/60%；致命伤害不死并回复50%生命，每场2次",
     ["eidolon1"]),
    ("星魂2-增笑", "我方全体欢愉伤害增笑15%", ["eidolon2"]),
    ("星魂2-扩展", "施放终结技时，使除真珠和底本外的其他欢愉角色也触发行动提前",
     ["eidolon2", "advance"]),
    ("星魂2-加倍", "终结技提供的额外回合开始时获得的好活当赏和笑点提高100%", ["eidolon2"]),
    ("星魂3/5", "技能等级+2/+1（各槽位）", ["eidolon3", "eidolon5"]),
    ("星魂4", "欢愉技提供的欢愉伤害倍率提高原倍率的100%", ["eidolon4"]),
    ("星魂6-穿透", "【深度学习】时我方全体全属性抗性穿透+20%", ["eidolon6_raises"]),
    ("星魂6-伤害", "强化普攻额外造成240%冰属性欢愉伤害，按底本属性计算", ["eidolon6_extra"]),
]


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    blob = json.dumps(data, ensure_ascii=False)

    lines = []
    missing = []
    for label, clause, keywords in CLAUSES:
        found = [r["id"] for r in rules if any(k in r["id"] for k in keywords)] if keywords else []
        # Fall back to a whole-file search for spellings that live inside a rule rather than its id.
        in_file = keywords and any(k in blob for k in keywords)
        status = "OK   " if found else ("IN-FILE?" if in_file else "MISSING")
        if status == "MISSING":
            missing.append(label)
        lines.append(f"{status} {label}")
        lines.append(f"       page: {clause}")
        lines.append(f"       rules: {found if found else ('(keyword appears in the file, not as a rule id)' if in_file else '(nothing)')}")
        lines.append("")

    lines.append("== MISSING (no rule id matched) ==")
    for label in missing:
        lines.append("  - " + label)
    lines.append("")
    lines.append(f"total rules in the file: {len(rules)}")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))
    print("MISSING:", missing)


if __name__ == "__main__":
    main()
