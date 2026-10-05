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
PEERAGE = "爵位"
MERIT = "军功"
CHARGE = "充能"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in (PIERCE, ENDS))]

rules.append({
    "on": "CAST_SETUP",
    "id": PIERCE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "CAST_SKILL", "skill": "SKILL", "target": "attacker"}],
    "source": ("1412 刻律德菈（文档 `:67`/`:68`）：「…对敌方目标施放战技时触发**奇袭**」"
               "＋定义「**奇袭**：复制一次即将施放的技能并**提前施放**，随后施放原技能」"),
    "note": ("⭐ 2026-10-02：`CAST_SETUP`（引擎自述的**前置钩子** ✓）＋`CAST_SKILL`"
             "（“**使其立即施放 1 次**” ✓）。⚠ 防递归在**引擎侧** ✓"
             "（`castSkill` 看施放栈：`currentCast().outer() != null` ⇒ 跳过 ✓）—— "
             "因为条件 DSL 的 `!` **只能否定队伍条件** ✗，内容侧写不出来 ✗。"),
})
rules.append({
    "on": "INSERTED_CAST_END",
    "id": ENDS,
    "when": ["actor has_state " + PEERAGE, "self_resource:" + CHARGE + " >= 6"],
    "do": [{"op": "SPEND_RESOURCE", "resource": CHARGE, "amount": 6},
           {"op": "REMOVE_STATE", "buff": PEERAGE, "target": "attacker"}],
    "source": ("1412 刻律德菈（文档 `:67`）：「**奇袭结束后，消耗 6 点充能使"
               "【爵位】变回【军功】**」"),
    "note": ("⭐ 2026-10-02：触发用**新建的 `INSERTED_CAST_END`** ✓（“被命令的施放结束” ✓）；"
             "“变回【军功】”就是**取消【爵位】** ✓（文档：「持有【爵位】的角色"
             "**被视为同时持有【军功】**」 ✓）；门槛 `self_resource:充能 >= 6` ✓ 保证不会花不起 ✗。"),
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
 * 1412：「奇袭：复制一次即将施放的技能并提前施放，随后施放原技能。**奇袭不会再次触发奇袭**」＋
 * 「奇袭结束后，消耗 6 点充能使【爵位】变回【军功】」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE: six casts promote the ally either way; the control merely removes 【爵位】 before the
 * peer's own skill, so the only difference is whether the copy happens.
 *
 * <p>⚠ The upper bound is the anti-recursion proof: two casts, not an unbounded chain.
 */
public class CoupDeMainTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";
    private static final String MERIT = "军功";
    private static final String CHARGE = "充能";

    /** ⭐ The peer's skill is cast twice -- the copy first, then the original -- and not forever. */
    @Test
    public void thePeerCastsItsSkillTwice() {
        double withCoup = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control deals damage (" + without + ")");
        Assertions.assertTrue(withCoup > without * 1.5,
                "「复制一次…提前施放，随后施放原技能」 (with=" + withCoup + ", without=" + without + ")");
        Assertions.assertTrue(withCoup < without * 2.5,
                "「奇袭不会再次触发奇袭」 -- two casts, not a chain (with=" + withCoup + ", without=" + without + ")");
    }

    /** ⭐ After the coup, she pays six Charge and the peerage reverts to the merit. */
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

        Assertions.assertEquals(charge - 6 + 2, owner.getResources().value(CHARGE), 1e-9,
                "「消耗 6 点充能」—— ❗ 而两次施放（复制 + 原技能）"
                        + "各给她 +1 点（【军功】那条：「施放普攻或战技时使刻律德菈获得 1 点充能」）"
                        + "，所以净变化是 -4 ✓ (before=" + charge + ")");
        Assertions.assertFalse(ally.getBuffManager().hasState(PEERAGE),
                "「使【爵位】变回【军功】」");
        Assertions.assertTrue(ally.getBuffManager().hasState(MERIT),
                "“变回【军功】” -- the merit is still there");
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
