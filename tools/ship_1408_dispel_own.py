"""1408: 「解除自身所有负面效果，随后造成…」 -- the dispel half (2026-10-02, item 42).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的强化攻击): 「**解除自身所有负面效果**，随后造成最多等同于卡厄斯兰那 1170% 攻击力的物理属性伤害。…」

WHY `DISPEL` IS RIGHT HERE (and was NOT in item 33): that item's sentence named a CLASS -- 「解除其**控制类**负面状态」 -- and DISPEL
settles as `removeDebuffs(amount)`, which has no class filter at all, so using it there would have swept away DOTs too. This
sentence says 「**所有**负面效果」, no class named: `removeDebuffs` is exactly that, and a shipped reader spells "all" the same way
(1310's `talent_dispel_when_energy_full`, whose source is the same sentence family: 「当能量恢复至上限时解除自身所有负面效果」, uses
`{"op": "DISPEL", "amount": 99, "target": "self"}`).

\u2b50 WHY `CAST_SETUP`: the sentence orders it -- dispel FIRST, then the damage. `CAST_SETUP` is the engine's pre-cast hook, so the
dispel is settled before the swing; riding an after-attack event would put it on the wrong side of the damage.

\u26a0 REGISTERED, not shipped, from the same sentence: 「造成最多等同于卡厄斯兰那 1170% 攻击力的…伤害。其中，每消耗 1 点【毁伤】造成 4 次伤害…
消耗 4 点【毁伤】时额外造成…450%…由敌方全体均分」 -- the 毁伤-counted multi-hit structure and "split evenly" are both still open
(the latter has no spelling anywhere in the tree: searched for 均分, only document text matches).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationDispelsTest.java"
RULE = "transformation_dispels_own_debuffs"
STATE = "\u53d8\u8eab"
DOT = "\u89e6\u7535"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "CAST_SETUP",
    "id": RULE,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "DISPEL", "amount": 99, "target": "self"}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u5f3a\u5316\u653b\u51fb\uff09\uff1a\u300c**\u89e3\u9664\u81ea\u8eab\u6240\u6709\u8d1f\u9762\u6548\u679c**\uff0c"
               "\u968f\u540e\u9020\u6210\u6700\u591a\u7b49\u540c\u4e8e\u5361\u5384\u65af\u5170\u90a3 1170% \u653b\u51fb\u529b\u7684\u7269\u7406\u5c5e\u6027\u4f24\u5bb3\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u2b50 **\u8fd9\u91cc `DISPEL` \u662f**\u5bf9\u7684**** \u2713\uff08\u4e0e\u7b2c 33 \u4ef6\u76f8\u53cd \u2713\uff09\u2014\u2014 "
             "\u90a3\u53e5\u540d\u4e86**\u7c7b\u522b**\uff08\u300c\u63a7\u5236\u7c7b\u300d\u2717\uff09\uff0c\u800c `DISPEL` \u843d\u5730\u662f `removeDebuffs(amount)` \u2713\uff08**\u65e0\u7c7b\u522b\u8fc7\u6ee4** \u2717\uff09\uff1b"
             "\u672c\u53e5\u8bf4\u7684\u662f\u300c**\u6240\u6709**\u8d1f\u9762\u6548\u679c\u300d\u2713 \u21d2 \u6b63\u662f\u5b83 \u2713\u3002\u26a0 `amount: 99` **\u9010\u5b57\u6284\u81ea `1310`** \u2713"
             "\uff08\u540c\u53e5\u5148\u4f8b\uff1a\u300c\u5f53\u80fd\u91cf\u6062\u590d\u81f3\u4e0a\u9650\u65f6\u89e3\u9664\u81ea\u8eab\u6240\u6709\u8d1f\u9762\u6548\u679c\u300d \u2713\uff09\u3002"
             "\u2757 \u7528 **`CAST_SETUP`** \u2713 \u2014\u2014 \u53e5\u5b50\u7684\u987a\u5e8f\u662f\u201c**\u5148\u89e3\u9664\u3001\u540e\u9020\u4f24**\u201d \u2713\uff0c\u800c `CAST_SETUP` \u662f**\u65bd\u653e\u524d**\u7684\u94a9\u5b50 \u2713\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformed form dispels its own debuffs")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408\uff1a\u300c**\u89e3\u9664\u81ea\u8eab\u6240\u6709\u8d1f\u9762\u6548\u679c**\uff0c\u968f\u540e\u9020\u6210\u2026\u300d (2026-10-02).
 *
 * <p>\u2b50 SAME SCENE, ONE VARIABLE, and the DOT is applied AFTER the transformation: item 41 made the transformed form immune to
 * CONTROLS, so a control could not be used here even though it is a debuff -- a Thunder DOT is used instead, and the immunity
 * does not touch it.
 */
public class TransformationDispelsTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";
    private static final String DOT = "\u89e6\u7535";

    /** \u2b50 Transformed: her own cast strips the debuff. */
    @Test
    public void theTransformedFormStripsItsDebuffs() {
        Assertions.assertFalse(debuffedAfterCast(true),
                "\u300c\u89e3\u9664\u81ea\u8eab\u6240\u6709\u8d1f\u9762\u6548\u679c\u300d");
    }

    /** \u26a0 Untransformed: the same cast leaves it there. */
    @Test
    public void withoutTheTransformationNothingIsStripped() {
        Assertions.assertTrue(debuffedAfterCast(false),
                "\u300c\u5361\u5384\u65af\u5170\u90a3\u2026\u89e3\u9664\u300d-- the dispel belongs to the transformation");
    }

    // ==================================================================

    private static boolean debuffedAfterCast(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character applier = CharacterFactory.create(APPLIER, 80);
        applier.setTriggerTable(new TriggerTable(APPLIER, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of("actor == self"), dot()))));

        Battle battle = new Battle(List.of(owner, applier),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
            Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        } else {
            Assertions.assertFalse(owner.getBuffManager().hasState(STATE), "precondition: not transformed");
        }

        // \u2b50 The debuff lands NOW -- after the transformation (see the class comment), and before her own cast.
        Skill theirs = applier.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(theirs, "precondition: the applier has a skill");
        SkillExecutor.execute(battle, theirs, applier, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(DOT),
                "precondition: the DOT landed on her (state name " + DOT + ")");

        Skill hers = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(hers, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, hers, owner, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return owner.getBuffManager().hasState(DOT);
    }

    /** A Thunder DOT on the other ally. */
    private static EffectSpec dot() {
        EffectSpec e = new EffectSpec();
        TriggerSpecs.set(e, "op", "APPLY_DOT");
        TriggerSpecs.set(e, "element", "Thunder");
        // \u26a0 `baseChance`, the JAVA field name: `TriggerSpecs.set` uses reflection, so the JSON key (`base_chance`) is not what it
        // wants -- it failed loudly with "cannot set base_chance on class EffectSpec".
        TriggerSpecs.set(e, "baseChance", 1.0);
        TriggerSpecs.set(e, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(e, "percent", 2.9);
        TriggerSpecs.set(e, "turns", 2);
        TriggerSpecs.set(e, "target", "other_allies");
        return e;
    }
}
''')
print("ok   judge written")
