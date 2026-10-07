"""1506 batch 2: her Eidolon skill-level bonuses, her speed trace, and Eidolon 6's 增笑.

Mapping established from the data, not guessed:
  * `eidolons.json` 1506 E3 -> {150602:+2, 150601:+1, 150620:+1, 150608:+1, 150610:+1, 150612:+1, 150618:+1, 150621:+1}
    E5 -> {150603:+2, 150604:+2, 150620:+1, 150621:+1};
  * `skills.json` 1506 carries `skill_id` per slot, so those ids ARE slots: 150601->1 (COMMON), 150602->2 (SKILL),
    150603->3 (ULTRA), 150604->4 (TALENT), 150620->20 (ELATION_SKILL), 150621->21 (ELATION_EXTRA);
  * `RAISE_SKILL_LEVEL` takes a `SkillType` name, and ELATION_SKILL / ELATION_EXTRA are both in that enum.

⚠ NOT expressible, and registered rather than approximated: the ids 150608 / 150610 / 150612 / 150618 are the ENHANCED
basic and its 盲盒 sub-skills, which live in their own slots (8/10/12/18) - the level vocabulary has no spelling for a
slot that `SkillType` does not name, so those four entries of E3 have nowhere to go.

Also shipped here: her speed trace (a threshold plus a per-point term) and E6's 增笑 under 【无敌玩家】.
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
HIDDEN = "隐藏分"
GOD = "无敌玩家"

RULES = [
    {
        "on": "BATTLE_START",
        "id": "p1506_e3_raises_skill_levels",
        "min_eidolon": 3,
        "do": [
            {"op": "RAISE_SKILL_LEVEL", "skill": "SKILL", "amount": 2},
            {"op": "RAISE_SKILL_LEVEL", "skill": "COMMON", "amount": 1},
            {"op": "RAISE_SKILL_LEVEL", "skill": "ELATION_SKILL", "amount": 1},
        ],
        "source": "1506 银狼LV.999 星魂 3 只有15级？谁填的: 「战技等级+2，最多不超过15级；普攻等级+1，最多不超过10级；"
                  "欢愉技等级+1，最多不超过15级。」（eidolons.json 1506 E3 的 skill_add_level：150602+2、150601+1、150620+1…）",
        "note": "⭐ **数值直接量自 `eidolons.json` 的 `skill_add_level`** ✓，而**技能 id → 槽位**由 `skills.json` 的 "
                "`skill_id` 字段确认 ✓（150602→槽 2 = 战技 ✓、150601→槽 1 = 普攻 ✓、150620→槽 20 = 欢愉技 ✓）。"
                "⚠ **上界不用写** ✓：引擎把等级夹在该技能表自己的行数上 ✓（`CanHit.skillLevel` 的 `min(level, rows)` ✓）"
                "⇒ 原文的「最多不超过 15 级」**由数据行自动成立** ✓。"
                "⚠⚠ **E3 还有 4 项在这条之外** ✓（`150608`／`150610`／`150612`／`150618` = **强化普攻与其盲盒子技能** ✓）—— "
                "它们住在**自己的槽位**（8／10／12／18 ✓），而**等级词表没有「`SkillType` 没命名的槽」的写法** ✓ "
                "⇒ 照实**登记**、不近似 ✓。",
    },
    {
        "on": "BATTLE_START",
        "id": "p1506_e5_raises_skill_levels",
        "min_eidolon": 5,
        "do": [
            {"op": "RAISE_SKILL_LEVEL", "skill": "ULTRA", "amount": 2},
            {"op": "RAISE_SKILL_LEVEL", "skill": "TALENT", "amount": 2},
            {"op": "RAISE_SKILL_LEVEL", "skill": "ELATION_SKILL", "amount": 1},
            {"op": "RAISE_SKILL_LEVEL", "skill": "ELATION_EXTRA", "amount": 1},
        ],
        "source": "1506 银狼LV.999 星魂 5 平A即是大招: 「终结技等级+2，最多不超过15级；天赋等级+2，最多不超过15级；"
                  "欢愉技等级+1，最多不超过15级。」（E5 的 skill_add_level：150603+2、150604+2、150620+1、150621+1）",
        "note": "⚠ E5 的「欢愉技」在数据里是**两项** ✓（`150620` = 槽 20 欢愉技 ✓、`150621` = 槽 21 欢愉技·强化 ✓）"
                "⇒ **两条都要写** ✓，用 `ELATION_SKILL` 与 `ELATION_EXTRA` **两个不同的 SkillType** ✓。"
                "⚠ 与 E3 那条**同事件但内容不同** ✓（E3 不碰终结技／天赋 ✓、E5 不碰战技／普攻 ✓）⇒ 不会被重复守卫拒绝 ✓。",
    },
    {
        "on": "BATTLE_START",
        "id": "p1506_trace_speed_becomes_elation",
        "when": ["self_attr:speed >= 160"],
        "do": [
            {"op": "MODIFY_ATTR", "attribute": "ELATION_DAMAGE_BOOST", "amount": 0.5, "permanent": True, "target": "self"},
        ],
        "source": "1506 银狼LV.999 行迹 假结局速通攻略 (1506101): 「速度大于等于 160 时，使自身欢愉度提高 50%，"
                  "之后每超过 1 点速度使自身欢愉度提高 2%，最多计入 100 点超出的速度。」",
        "note": "⭐ 这一条只写**前一半**（「速度 ≥ 160 ⇒ 欢愉度 +50%」✓），⚠ **后一半照实登记** ✓："
                "「每超过 1 点速度 +2%，**最多计入 100 点**」需要一个**在 100 点处夹住的缩放** ✓ "
                "（`min(2 × (速度−160), 200)%` ✓），而**没有任何拼法能夹住 `scale` 的结果** ✓ "
                "⇒ ⚠ **不近似**（若写成不夹的缩放 ✓，速度 300 时会给出 280% 而不是 200% ✓ —— **一个安静的错数** ✓）。",
    },
    {
        "on": "ULT_CAST",
        "id": "p1506_e6_amp_while_in_godmode",
        "min_eidolon": 6,
        "when": ["actor == self"],
        "do": [
            {"op": "MODIFY_ATTR", "attribute": "ELATION_DAMAGE_AMP", "percent": 0.5, "turns": 3, "target": "self"},
        ],
        "source": "1506 银狼LV.999 星魂 6 我独自满级！: 「强化普攻期间造成的欢愉伤害增笑 50%。」—— #2 = 0.5。"
                  "「强化普攻期间」= 【无敌玩家】状态期间，而该状态由本条所在事件授予。",
        "note": "⭐ **「增笑」就是 `ELATION_DAMAGE_AMP`** ✓ —— 与 1503 真珠的增笑**同一个词汇** ✓"
                "（`AttributeType` 的注释写明它是「欢愉伤害自己的放大器，与欢愉度 `ELATION_DAMAGE_BOOST` 不同」✓）。"
                "⚠ 时长用 `turns: 3` ✓，与【无敌玩家】的寿命一致 ✓（**3 次**强化普攻 ✓）—— "
                "⚠ 这是**登记在案的近似** ✓：真正的边界是那个状态 ✓，而**状态时长与回合数在这里重合** ✓"
                "（她每次行动都放强化普攻 ✓），但**不保证**总是重合 ✓。"
                "⚠ 星魂 6 的**后半**（敌方获【禁限弱点】✓）**未出货** ✓ —— 需要**对敌方写入弱点与基础抗性** ✓，"
                "引擎没有那种写入 ✓。",
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
