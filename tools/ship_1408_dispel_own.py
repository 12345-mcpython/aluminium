"""1408: 「解除自身所有负面效果，随后造成…」 -- the dispel half (2026-10-02, item 42).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的强化攻击): 「**解除自身所有负面效果**，随后造成最多等同于卡厄斯兰那 1170% 攻击力的物理属性伤害。…」

WHY `DISPEL` IS RIGHT HERE (and was NOT in item 33): that item's sentence named a CLASS -- 「解除其**控制类**负面状态」 -- and DISPEL
settles as `removeDebuffs(amount)`, which has no class filter at all, so using it there would have swept away DOTs too. This
sentence says 「**所有**负面效果」, no class named: `removeDebuffs` is exactly that, and a shipped reader spells "all" the same way
(1310's `talent_dispel_when_energy_full`, whose source is the same sentence family: 「当能量恢复至上限时解除自身所有负面效果」, uses
`{"op": "DISPEL", "amount": 99, "target": "self"}`).

⭐ WHY `CAST_SETUP`: the sentence orders it -- dispel FIRST, then the damage. `CAST_SETUP` is the engine's pre-cast hook, so the
dispel is settled before the swing; riding an after-attack event would put it on the wrong side of the damage.

⚠ REGISTERED, not shipped, from the same sentence: 「造成最多等同于卡厄斯兰那 1170% 攻击力的…伤害。其中，每消耗 1 点【毁伤】造成 4 次伤害…
消耗 4 点【毁伤】时额外造成…450%…由敌方全体均分」 -- the 毁伤-counted multi-hit structure and "split evenly" are both still open
(the latter has no spelling anywhere in the tree: searched for 均分, only document text matches).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationDispelsTest.java"
RULE = "transformation_dispels_own_debuffs"
STATE = "变身"
DOT = "触电"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "CAST_SETUP",
    "id": RULE,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "DISPEL", "amount": 99, "target": "self"}],
    "source": ("1408 白厄（文档，卡厄斯兰那的强化攻击）：「**解除自身所有负面效果**，"
               "随后造成最多等同于卡厄斯兰那 1170% 攻击力的物理属性伤害」"),
    "note": ("⭐ 2026-10-02：⭐ **这里 `DISPEL` 是**对的**** ✓（与第 33 件相反 ✓）—— "
             "那句名了**类别**（「控制类」✗），而 `DISPEL` 落地是 `removeDebuffs(amount)` ✓（**无类别过滤** ✗）；"
             "本句说的是「**所有**负面效果」✓ ⇒ 正是它 ✓。⚠ `amount: 99` **逐字抄自 `1310`** ✓"
             "（同句先例：「当能量恢复至上限时解除自身所有负面效果」 ✓）。"
             "❗ 用 **`CAST_SETUP`** ✓ —— 句子的顺序是“**先解除、后造伤**” ✓，而 `CAST_SETUP` 是**施放前**的钩子 ✓。"),
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
 * 1408：「**解除自身所有负面效果**，随后造成…」 (2026-10-02).
 *
 * <p>⭐ SAME SCENE, ONE VARIABLE, and the DOT is applied AFTER the transformation: item 41 made the transformed form immune to
 * CONTROLS, so a control could not be used here even though it is a debuff -- a Thunder DOT is used instead, and the immunity
 * does not touch it.
 */
public class TransformationDispelsTest {
    private static final int OWNER = 1408;
    private static final int APPLIER = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final String DOT = "触电";

    /** ⭐ Transformed: her own cast strips the debuff. */
    @Test
    public void theTransformedFormStripsItsDebuffs() {
        Assertions.assertFalse(debuffedAfterCast(true),
                "「解除自身所有负面效果」");
    }

    /** ⚠ Untransformed: the same cast leaves it there. */
    @Test
    public void withoutTheTransformationNothingIsStripped() {
        Assertions.assertTrue(debuffedAfterCast(false),
                "「卡厄斯兰那…解除」-- the dispel belongs to the transformation");
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

        // ⭐ The debuff lands NOW -- after the transformation (see the class comment), and before her own cast.
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
        // ⚠ `baseChance`, the JAVA field name: `TriggerSpecs.set` uses reflection, so the JSON key (`base_chance`) is not what it
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
