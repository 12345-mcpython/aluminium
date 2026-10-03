"""8007: 【迷迷的声援】's 28% true-damage rider (2026-10-02).

Document, verbatim (8007_开拓者.html:143): 「持有【迷迷的声援】的目标每造成 1 次伤害，都会再额外造成 1 次
<b>等同于原伤害 28%</b>的真实伤害。」 and :144 「真实伤害：不受任何效果所影响的无属性伤害，本次伤害不视为造成了 1 次攻击。」

Every word of this rule was measured before it was written:
  * the op shape is 1415's in-tree rider, verbatim: `on: DAMAGE_SETTLED`, `scale: "original_damage"`,
    `damage_type: "TRUE"`, `target: "target"`;
  * `DAMAGE_TYPE_READERS` includes `DAMAGE`, so `damage_type` is legal here;
  * for `damage_type: "TRUE"` the base is `ctx.amount() * share` -- the amount the victim actually took, with no
    division (the division exists only for riders that do NOT skip the zones);
  * the element is `Ice` because the DATA says so (`character_data.json`: 8007 `"attribute": "ice"`), not because it was
    guessed -- `DamageElement` has no element-less member, and it is only a carried label on true damage;
  * the rules stay on 8007's OWN table: the rider's actor is the damage dealer, and the condition DSL's subject is free
    (`target has_state 触电` is its documented example), so `actor has_state 迷迷的声援` can name the holder.

⛔ Not done, on purpose: nothing is approximated. The judge drives REAL content (his skill lays the cheer, the ally then
attacks) rather than rebuilding a table -- the engine's own note records that the last time this rider was probed with a
hand-built table, a CORRECT implementation was rolled back three times because `level_convention` had been dropped too.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/8007.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/CheerTrueDamageRiderTest.java"
RULE = "cheer_true_damage_rider"
CHEER = "\u8ff7\u8ff7\u7684\u58f0\u63f4"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "DAMAGE_SETTLED",
    "id": RULE,
    "when": ["actor has_state " + CHEER, "actor is_ally", "damage_is_attack"],
    "do": [{"op": "DAMAGE", "scale": "original_damage", "percent": 0.28,
            "element": "Ice", "damage_type": "TRUE", "target": "target"}],
    "source": ("8007 \u5f00\u62d3\u8005 \u6218\u6280\uff08\u6587\u6863 `:143`\uff09\uff1a"
               "\u300c\u6301\u6709\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\u7684\u76ee\u6807\u6bcf\u9020\u6210 1 \u6b21\u4f24\u5bb3\uff0c"
               "\u90fd\u4f1a\u518d\u989d\u5916\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u539f\u4f24\u5bb3 28%**\u7684\u771f\u5b9e\u4f24\u5bb3\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u5199\u6cd5\u9010\u5b57\u7167\u62c4 **`1415.json` \u7684 `zone_true_damage_rider`** \u2713"
             "\uff08`DAMAGE_SETTLED` \u2713\uff0f`scale: \"original_damage\"` \u2713\uff0f`damage_type: \"TRUE\"` \u2713\uff09\uff1b"
             "`percent: 0.28` \u2713 \u4e0e `element: \"Ice\"` \u2713 \u5747\u51fa\u81ea\u6570\u636e\uff08\u4ed6\u7684 `attribute` \u662f `ice` \u2713\uff09\u3002"
             "\u26a0 \u65e0\u5c5e\u6027\u5728\u5f15\u64ce\u91cc\u662f\u201c**\u8df3\u8fc7\u5168\u90e8\u4f24\u5bb3\u533a**\u201d\u5b9e\u73b0\u7684 \u2713\uff0c`element` \u53ea\u662f\u968f\u884c\u6807\u7b7e \u2713\u3002"),
})

if is_dict:
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   8007.json: the cheer's 28% true-damage rider")

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
 * 8007\uff1a\u300c\u6301\u6709\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\u7684\u76ee\u6807\u6bcf\u9020\u6210 1 \u6b21\u4f24\u5bb3\uff0c\u90fd\u4f1a\u518d\u989d\u5916\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u539f\u4f24\u5bb3 28%**\u7684\u771f\u5b9e\u4f24\u5bb3\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY, and driven by REAL content: his skill lays the cheer on the ally, the ally then attacks. The control battle is
 * the same fight without the cheer, so the EXCESS the enemy loses must be exactly 28% of what it lost in the control --
 * that is the sentence, and nothing else changes between the two runs (`level_convention` stays in place: no table is rebuilt).
 */
public class CheerTrueDamageRiderTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    /** \u2b50 The holder's damage is raised by exactly the 28% the sentence states. */
    @Test
    public void theHolderDealsTwentyEightPercentMore() {
        double with = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control battle deals damage (" + without + ")");
        Assertions.assertEquals(without * 1.28, with, without * 0.05,
                "\u300c\u989d\u5916\u9020\u6210 1 \u6b21\u7b49\u540c\u4e8e\u539f\u4f24\u5bb3 28% \u7684\u771f\u5b9e\u4f24\u5bb3\u300d (with=" + with + ", without=" + without + ")");
    }

    private static double damageDealt(boolean cheer) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // \u2b50 BOTH branches summon: his skill summons \u8ff7\u8ff7, and the first draft only cast it in the "with" branch -- so the
        // excess contained the memosprite's own damage too. Same scene, one variable: the control removes the cheer instead.
        Skill his = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(his, "precondition: he has a skill");
        SkillExecutor.execute(battle, his, owner, List.of(ally));
        battle.processRequests();
        if (!cheer) {
            ally.getBuffManager().removeState(CHEER);
            battle.processRequests();
        }
        double before = battle.enemies.get(0).getCurrentHp();
        Skill hers = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(hers, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, hers, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
''')
print("ok   judge written")
