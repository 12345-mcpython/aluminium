"""1408: 「当白厄成为其他任意目标的技能目标时，获得 1 点【火种】。若施放者为白厄的队友，还会使白厄的暴击伤害提高 30%，持续 3 回合。」
(2026-10-02, item 38).

Document, verbatim (1408_白厄.html, 白厄的天赋): 「…**当白厄成为其他任意目标的技能目标时，获得 1 点【火种】**。**若施放者为白厄的队友，
还会使白厄的暴击伤害提高 30%，持续 3 回合**。」

SHAPE, taken verbatim from a shipped reader rather than guessed: 1002's `talent_wind_pen` is the same moment --
`on: SKILL_CAST`, `when: ["actor is_ally", "target == self"]` -- and its own note explains why: SKILL_CAST is the cast event
that CARRIES the aimed unit ("`aimed`"), so "I became the target" is `target == self`. That is exactly「成为…的技能目标」.

TWO CLAUSES, TWO RULES, because the second has a narrower condition than the first:
  * the 1 point of Coreflame needs only "someone aimed a skill at her" -- the document says 其他任意目标, i.e. ally OR enemy;
  * the +30% crit damage additionally needs the caster to be a teammate, which is `actor is_ally` (the same spelling 8007's
    shipped rider uses).
⚠ The first rule cannot say "anyone but herself" (the DSL's `!` negates only PARTY conditions, per `TriggerTable`), and it does
not need to: she cannot aim her own skill at herself.
⚠ The +30% carries NO `buff:` link: it is "持续 3 回合", a duration of its own (the distinction item 36 established).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/SkillTargetCoreflameTest.java"
CORE_RULE = "talent_coreflame_on_being_targeted"
CRIT_RULE = "talent_crit_damage_when_ally_targets_her"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (CORE_RULE, CRIT_RULE))]

rules.append({
    "on": "SKILL_CAST",
    "id": CORE_RULE,
    "when": ["target == self"],
    "do": [{"op": "GAIN_RESOURCE", "resource": "火种", "amount": 1}],
    "source": ("1408 白厄（文档，白厄的天赋）：「当白厄成为**其他任意目标**的技能目标时，"
               "**获得 1 点【火种】**」"),
    "note": ("⭐ 2026-10-02：形状**逐字取自已出货的 `1002.talent_wind_pen`** ✓"
             "（`SKILL_CAST` 是**带被瞄准单位**的施放事件 ✓ ⇒ “成为目标”＝`target == self` ✓）。"
             "⚠ “其他任意目标”（敌或友）无需额外条件 ✓，因为她无法把自己的技能瞄自己 ✓。"),
})
rules.append({
    "on": "SKILL_CAST",
    "id": CRIT_RULE,
    "when": ["actor is_ally", "target == self"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.30,
            "turns": 3, "target": "self"}],
    "source": ("1408 白厄（文档，白厄的天赋）：「**若施放者为白厄的队友**，还会使白厄的"
               "**暴击伤害提高 30%**，持续 **3** 回合」"),
    "note": ("⭐ 2026-10-02：第二半的条件**比第一半窄** ✓（`actor is_ally` ✓ —— 与 `8007` 已出货的骑手同拼写 ✓）"
             "⇒ 所以**它是单独一条规则** ✓；❗ **不绑状态** ✓（“持续 3 回合”是它自己的时长 ✓—— 第 36 件已立的区分 ✓）。"),
})

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: being targeted grants Coreflame, and an ally's skill raises her crit damage")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408：「当白厄成为其他任意目标的技能目标时，获得 1 点【火种】。若施放者为白厄的队友，还会使白厄的
 * 暴击伤害提高 30%，持续 3 回合」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: 1414 -- whose skill is a shield aimed at a teammate -- casts it either AT 1408 or AT THE ENEMY. The
 * only difference is who was aimed at, which is exactly what the sentence is about.
 */
public class SkillTargetCoreflameTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int SUPPORT = 1414;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "火种";

    /** ⭐ Aimed at: she gains a point of Coreflame and 30% crit damage. */
    @Test
    public void beingTargetedGrantsCoreflameAndCritDamage() {
        double[] aimedAtHer = scene(true);
        Assertions.assertEquals(1.0, aimedAtHer[0], EPS, "「获得 1 点【火种】」");
        Assertions.assertEquals(0.30, aimedAtHer[1], EPS, "「暴击伤害提高 30%」");
    }

    /** ⚠ Aimed at an enemy instead: the sentence has not started. */
    @Test
    public void aimingElsewhereChangesNothing() {
        double[] aimedAway = scene(false);
        Assertions.assertEquals(0.0, aimedAway[0], EPS, "「成为…技能目标时」 -- she was not the target");
        Assertions.assertEquals(0.0, aimedAway[1], EPS, "…so there is no crit damage either");
    }

    // ==================================================================

    /** returns { Coreflame gained, crit damage gained }. */
    private static double[] scene(boolean aimAtHer) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character support = CharacterFactory.create(SUPPORT, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, support),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double coreBefore = owner.getResources().value(COREFLAME);
        double critBefore = owner.getAttribute(AttributeType.CRIT_ATTACK).get();

        Skill skill = support.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: the teammate has a skill");
        // ⚠ 两条分支，而不是一个三元表达式：一个是 `Character`、一个是 `CanHit`，没有共同类型可声明。
        if (aimAtHer) {
            SkillExecutor.execute(battle, skill, support, List.of(owner));
        } else {
            SkillExecutor.execute(battle, skill, support, List.of(battle.enemies.get(0)));
        }
        battle.processRequests();

        return new double[]{
                owner.getResources().value(COREFLAME) - coreBefore,
                owner.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore};
    }
}
''')
print("ok   judge written")
