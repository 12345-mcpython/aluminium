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
\u26a0 The first rule cannot say "anyone but herself" (the DSL's `!` negates only PARTY conditions, per `TriggerTable`), and it does
not need to: she cannot aim her own skill at herself.
\u26a0 The +30% carries NO `buff:` link: it is "持续 3 回合", a duration of its own (the distinction item 36 established).
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
    "do": [{"op": "GAIN_RESOURCE", "resource": "\u706b\u79cd", "amount": 1}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u767d\u5384\u7684\u5929\u8d4b\uff09\uff1a\u300c\u5f53\u767d\u5384\u6210\u4e3a**\u5176\u4ed6\u4efb\u610f\u76ee\u6807**\u7684\u6280\u80fd\u76ee\u6807\u65f6\uff0c"
               "**\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u5f62\u72b6**\u9010\u5b57\u53d6\u81ea\u5df2\u51fa\u8d27\u7684 `1002.talent_wind_pen`** \u2713"
             "\uff08`SKILL_CAST` \u662f**\u5e26\u88ab\u7784\u51c6\u5355\u4f4d**\u7684\u65bd\u653e\u4e8b\u4ef6 \u2713 \u21d2 \u201c\u6210\u4e3a\u76ee\u6807\u201d\uff1d`target == self` \u2713\uff09\u3002"
             "\u26a0 \u201c\u5176\u4ed6\u4efb\u610f\u76ee\u6807\u201d\uff08\u654c\u6216\u53cb\uff09\u65e0\u9700\u989d\u5916\u6761\u4ef6 \u2713\uff0c\u56e0\u4e3a\u5979\u65e0\u6cd5\u628a\u81ea\u5df1\u7684\u6280\u80fd\u7784\u81ea\u5df1 \u2713\u3002"),
})
rules.append({
    "on": "SKILL_CAST",
    "id": CRIT_RULE,
    "when": ["actor is_ally", "target == self"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.30,
            "turns": 3, "target": "self"}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u767d\u5384\u7684\u5929\u8d4b\uff09\uff1a\u300c**\u82e5\u65bd\u653e\u8005\u4e3a\u767d\u5384\u7684\u961f\u53cb**\uff0c\u8fd8\u4f1a\u4f7f\u767d\u5384\u7684"
               "**\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 30%**\uff0c\u6301\u7eed **3** \u56de\u5408\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u7b2c\u4e8c\u534a\u7684\u6761\u4ef6**\u6bd4\u7b2c\u4e00\u534a\u7a84** \u2713\uff08`actor is_ally` \u2713 \u2014\u2014 \u4e0e `8007` \u5df2\u51fa\u8d27\u7684\u9a91\u624b\u540c\u62fc\u5199 \u2713\uff09"
             "\u21d2 \u6240\u4ee5**\u5b83\u662f\u5355\u72ec\u4e00\u6761\u89c4\u5219** \u2713\uff1b\u2757 **\u4e0d\u7ed1\u72b6\u6001** \u2713\uff08\u201c\u6301\u7eed 3 \u56de\u5408\u201d\u662f\u5b83\u81ea\u5df1\u7684\u65f6\u957f \u2713\u2014\u2014 \u7b2c 36 \u4ef6\u5df2\u7acb\u7684\u533a\u5206 \u2713\uff09\u3002"),
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
 * 1408\uff1a\u300c\u5f53\u767d\u5384\u6210\u4e3a\u5176\u4ed6\u4efb\u610f\u76ee\u6807\u7684\u6280\u80fd\u76ee\u6807\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u3002\u82e5\u65bd\u653e\u8005\u4e3a\u767d\u5384\u7684\u961f\u53cb\uff0c\u8fd8\u4f1a\u4f7f\u767d\u5384\u7684
 * \u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 30%\uff0c\u6301\u7eed 3 \u56de\u5408\u300d (2026-10-02).
 *
 * <p>\u2b50 SAME SCENE, ONE VARIABLE: 1414 -- whose skill is a shield aimed at a teammate -- casts it either AT 1408 or AT THE ENEMY. The
 * only difference is who was aimed at, which is exactly what the sentence is about.
 */
public class SkillTargetCoreflameTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int SUPPORT = 1414;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "\u706b\u79cd";

    /** \u2b50 Aimed at: she gains a point of Coreflame and 30% crit damage. */
    @Test
    public void beingTargetedGrantsCoreflameAndCritDamage() {
        double[] aimedAtHer = scene(true);
        Assertions.assertEquals(1.0, aimedAtHer[0], EPS, "\u300c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d");
        Assertions.assertEquals(0.30, aimedAtHer[1], EPS, "\u300c\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 30%\u300d");
    }

    /** \u26a0 Aimed at an enemy instead: the sentence has not started. */
    @Test
    public void aimingElsewhereChangesNothing() {
        double[] aimedAway = scene(false);
        Assertions.assertEquals(0.0, aimedAway[0], EPS, "\u300c\u6210\u4e3a\u2026\u6280\u80fd\u76ee\u6807\u65f6\u300d -- she was not the target");
        Assertions.assertEquals(0.0, aimedAway[1], EPS, "\u2026so there is no crit damage either");
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
        Character aimed = aimAtHer ? owner : battle.enemies.get(0);
        SkillExecutor.execute(battle, skill, support, List.of(aimed));
        battle.processRequests();

        return new double[]{
                owner.getResources().value(COREFLAME) - coreBefore,
                owner.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore};
    }
}
''')
print("ok   judge written")
