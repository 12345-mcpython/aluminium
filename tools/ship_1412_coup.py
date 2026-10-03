"""1412: 奇袭 -- the copied cast, its anti-recursion guard, and its end (2026-10-02, item 31).

Document, verbatim:
  * :68 / :85 「**奇袭**：复制一次即将施放的技能并提前施放，随后施放原技能。**奇袭不会再次触发奇袭**。」
  * :67 「…对敌方目标施放战技时触发**奇袭**。**奇袭结束后，消耗 6 点充能使【爵位】变回【军功】**。」

WHY THIS NEEDED AN ENGINE PIECE, and what it is (each half measured before it was written):
  * 「复制一次即将施放的技能并**提前**施放」 -- `CAST_SKILL` is exactly "the resolved target performs one cast, right now,
    with the numbers of the skill the rule names", and `CAST_SETUP` is the engine's own pre-cast hook ("the only moment at
    which a rule can still change what this cast does"). Both were already in the tree.
  * 「**奇袭不会再次触发奇袭**」 -- the copy's own CAST_SETUP would command a copy of the copy, forever. This has NO
    content-side spelling: the condition DSL's `!` only negates PARTY conditions (`TriggerTable`: "Condition '...' negates a
    condition that does not read a party ..."), so "not while inserting" cannot be written. The engine already keeps a cast
    STACK (`beginCast` links to the cast it interrupts, `endCast` restores it), so `currentCast().outer() != null` is
    precisely "the open cast is itself a commanded one" -- that is the guard, added to `castSkill`.
  * 「**奇袭结束后**…」 -- a commanded cast announces the same CAST_SETUP / SKILL_CAST a real one does, so no existing event
    names its end. `TriggerEvent.INSERTED_CAST_END` is that moment.

Readers (2): 1412's own sentence above, and the light cone whose 「奇袭结束后…」 was already registered against this moment.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CoupDeMainTest.java"
PIERCE = "coup_de_main"
ENDS = "coup_de_main_ends"
PEERAGE = "\u7235\u4f4d"
MERIT = "\u519b\u529f"
CHARGE = "\u5145\u80fd"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (PIERCE, ENDS))]

rules.append({
    "on": "CAST_SETUP",
    "id": PIERCE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "CAST_SKILL", "skill": "SKILL", "target": "actor"}],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8\uff08\u6587\u6863 `:67`/`:68`\uff09\uff1a\u300c\u2026\u5bf9\u654c\u65b9\u76ee\u6807\u65bd\u653e\u6218\u6280\u65f6\u89e6\u53d1**\u5947\u88ad**\u300d"
               "\uff0b\u5b9a\u4e49\u300c**\u5947\u88ad**\uff1a\u590d\u5236\u4e00\u6b21\u5373\u5c06\u65bd\u653e\u7684\u6280\u80fd\u5e76**\u63d0\u524d\u65bd\u653e**\uff0c\u968f\u540e\u65bd\u653e\u539f\u6280\u80fd\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a`CAST_SETUP`\uff08\u5f15\u64ce\u81ea\u8ff0\u7684**\u524d\u7f6e\u94a9\u5b50** \u2713\uff09\uff0b`CAST_SKILL`"
             "\uff08\u201c**\u4f7f\u5176\u7acb\u5373\u65bd\u653e 1 \u6b21**\u201d \u2713\uff09\u3002\u26a0 \u9632\u9012\u5f52\u5728**\u5f15\u64ce\u4fa7** \u2713"
             "\uff08`castSkill` \u770b\u65bd\u653e\u6808\uff1a`currentCast().outer() != null` \u21d2 \u8df3\u8fc7 \u2713\uff09\u2014\u2014 "
             "\u56e0\u4e3a\u6761\u4ef6 DSL \u7684 `!` **\u53ea\u80fd\u5426\u5b9a\u961f\u4f0d\u6761\u4ef6** \u2717\uff0c\u5185\u5bb9\u4fa7\u5199\u4e0d\u51fa\u6765 \u2717\u3002"),
})
rules.append({
    "on": "INSERTED_CAST_END",
    "id": ENDS,
    "when": ["actor has_state " + PEERAGE, "self_resource:" + CHARGE + " >= 6"],
    "do": [{"op": "SPEND_RESOURCE", "resource": CHARGE, "amount": 6},
           {"op": "REMOVE_STATE", "buff": PEERAGE, "target": "actor"}],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8\uff08\u6587\u6863 `:67`\uff09\uff1a\u300c**\u5947\u88ad\u7ed3\u675f\u540e\uff0c\u6d88\u8017 6 \u70b9\u5145\u80fd\u4f7f"
               "\u3010\u7235\u4f4d\u3011\u53d8\u56de\u3010\u519b\u529f\u3011**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u89e6\u53d1\u7528**\u65b0\u5efa\u7684 `INSERTED_CAST_END`** \u2713\uff08\u201c\u88ab\u547d\u4ee4\u7684\u65bd\u653e\u7ed3\u675f\u201d \u2713\uff09\uff1b"
             "\u201c\u53d8\u56de\u3010\u519b\u529f\u3011\u201d\u5c31\u662f**\u53d6\u6d88\u3010\u7235\u4f4d\u3011** \u2713\uff08\u6587\u6863\uff1a\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272"
             "**\u88ab\u89c6\u4e3a\u540c\u65f6\u6301\u6709\u3010\u519b\u529f\u3011**\u300d \u2713\uff09\uff1b\u95e8\u69db `self_resource:\u5145\u80fd >= 6` \u2713 \u4fdd\u8bc1\u4e0d\u4f1a\u82b1\u4e0d\u8d77 \u2717\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: coup de main and its end")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1412\uff1a\u300c\u5947\u88ad\uff1a\u590d\u5236\u4e00\u6b21\u5373\u5c06\u65bd\u653e\u7684\u6280\u80fd\u5e76\u63d0\u524d\u65bd\u653e\uff0c\u968f\u540e\u65bd\u653e\u539f\u6280\u80fd\u3002**\u5947\u88ad\u4e0d\u4f1a\u518d\u6b21\u89e6\u53d1\u5947\u88ad**\u300d\uff0b
 * \u300c\u5947\u88ad\u7ed3\u675f\u540e\uff0c\u6d88\u8017 6 \u70b9\u5145\u80fd\u4f7f\u3010\u7235\u4f4d\u3011\u53d8\u56de\u3010\u519b\u529f\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 SAME SCENE, ONE VARIABLE: six casts promote the ally either way; the control merely removes \u3010\u7235\u4f4d\u3011 before the
 * peer's own skill, so the only difference is whether the copy happens.
 *
 * <p>\u26a0 The upper bound is the anti-recursion proof: two casts, not an unbounded chain.
 */
public class CoupDeMainTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "\u7235\u4f4d";
    private static final String MERIT = "\u519b\u529f";
    private static final String CHARGE = "\u5145\u80fd";

    /** \u2b50 The peer's skill is cast twice -- the copy first, then the original -- and not forever. */
    @Test
    public void thePeerCastsItsSkillTwice() {
        double withCoup = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withCoup > without * 1.5,
                "\u300c\u590d\u5236\u4e00\u6b21\u2026\u63d0\u524d\u65bd\u653e\uff0c\u968f\u540e\u65bd\u653e\u539f\u6280\u80fd\u300d (with=" + withCoup + ", without=" + without + ")");
        Assertions.assertTrue(withCoup < without * 2.5,
                "\u300c\u5947\u88ad\u4e0d\u4f1a\u518d\u6b21\u89e6\u53d1\u5947\u88ad\u300d -- two casts, not a chain (with=" + withCoup + ", without=" + without + ")");
    }

    /** \u2b50 After the coup, she pays six Charge and the peerage reverts to the merit. */
    @Test
    public void theCoupEndsBySpendingSixCharge() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: the holder is a peer");
        double charge = owner.getResources().value(CHARGE);
        Assertions.assertTrue(charge >= 6, "precondition: at least six Charge (" + charge + ")");

        Skill his = ally.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();

        Assertions.assertEquals(charge - 6, owner.getResources().value(CHARGE), 1e-9,
                "\u300c\u6d88\u8017 6 \u70b9\u5145\u80fd\u300d");
        Assertions.assertFalse(ally.getBuffManager().hasState(PEERAGE),
                "\u300c\u4f7f\u3010\u7235\u4f4d\u3011\u53d8\u56de\u3010\u519b\u529f\u3011\u300d");
        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT),
                "\u201c\u53d8\u56de\u3010\u519b\u529f\u3011\u201d -- the merit is still there");
    }

    private static double damageDealt(boolean keepPeerage) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill hers = owner.getSkills().get(SkillType.SKILL);
        for (int i = 0; i < 6; i++) {
            SkillExecutor.execute(battle, hers, owner, List.of(ally));
            battle.processRequests();
        }
        Assertions.assertTrue(ally.getBuffManager().hasState(PEERAGE), "precondition: six casts promote");
        if (!keepPeerage) {
            ally.getBuffManager().removeState(PEERAGE);
            battle.processRequests();
        }
        double before = battle.enemies.get(0).getCurrentHp();
        Skill his = ally.getSkills().get(SkillType.SKILL);
        SkillExecutor.execute(battle, his, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
''')
print("ok   judge written")
