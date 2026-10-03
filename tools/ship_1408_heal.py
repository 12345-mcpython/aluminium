"""1408: 「变身期间…施放攻击后回复等同于自身生命上限 20% 的生命值」 (2026-10-02, item 37).

Document, verbatim (1408_白厄.html, 卡厄斯兰那的天赋): 「…变身期间攻击力提高 80%，生命上限提高 270%，**施放攻击后回复等同于自身生命上限 20%
的生命值**。…」

SHAPE, each piece measured elsewhere in this span:
  * "变身期间" is a STATE guard -> `when: ["self has_state 变身"]` (and the heal is a rule effect, not a modifier, so no `buff:`
    link is involved -- the guard is what scopes it);
  * "施放攻击后" is the attack-finished moment -> `TriggerEvent.ATTACK_FINISHED` (wired; the alternative spelling the engine also
    has is `ALLY_ATTACK`, and this judge tells them apart by whether the heal happens at all);
  * "回复等同于自身生命上限 20%" is a share of a Max HP the transformation itself has raised -> `HEAL` with
    `scale: "owner_max_hp"` + `percent: 0.20`, the same spelling `literalBase` documents for that scale.

\u26a0 The judge DAMAGES her first (a battle-path true-damage hit, the technique item 28 established) so that a heal is visible, then
lets her attack. The control is the same fight without the transformation.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1408.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationHealsOnAttackTest.java"
RULE = "transformation_heals_on_attack"
STATE = "\u53d8\u8eab"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    # \u26a0 `ALLY_ATTACK` (SkillExecutor:203), not `ATTACK_FINISHED` (Battle:2489): the judge measured no heal on the latter, and
    # the former is the one the skill path announces. It is BROADCAST to every member, so `actor == self` is required -- without
    # it a teammate's attack would heal her too.
    "on": "ALLY_ATTACK",
    "id": RULE,
    "when": ["actor == self", "self has_state " + STATE],
    "do": [{"op": "HEAL", "scale": "owner_max_hp", "percent": 0.20, "target": "self"}],
    "source": ("1408 \u767d\u5384\uff08\u6587\u6863\uff0c\u5361\u5384\u65af\u5170\u90a3\u7684\u5929\u8d4b\uff09\uff1a\u300c\u2026\u53d8\u8eab\u671f\u95f4\u2026"
               "**\u65bd\u653e\u653b\u51fb\u540e\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 20%** \u7684\u751f\u547d\u503c\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a\u4e09\u4ef6\u90fd\u662f\u672c\u6bb5\u5df2\u9a8c\u7684\u5f62\u72b6 \u2713\uff1a"
             "\u201c\u53d8\u8eab\u671f\u95f4\u201d\u7528**\u72b6\u6001\u6761\u4ef6** \u2713\uff08`self has_state \u53d8\u8eab` \u2713\u2014\u2014 \u672c\u53e5\u662f**\u89c4\u5219\u6548\u679c**\u800c\u975e\u4fee\u9970 \u2713 "
             "\u21d2 \u4e0d\u6d89\u53ca `buff:` \u7ed1\u5b9a \u2713\uff09\uff1b\u201c\u65bd\u653e\u653b\u51fb\u540e\u201d\u7528 `ATTACK_FINISHED` \u2713\uff1b"
             "\u201c\u56de 20% \u751f\u547d\u4e0a\u9650\u201d\u7528 `HEAL{scale: \"owner_max_hp\", percent: 0.20}` \u2713\u3002"
             "\u26a0 \u53d7\u91cf\u53d6**\u5f53\u65f6\u7684**\u751f\u547d\u4e0a\u9650 \u2713\uff08\u800c\u53d8\u8eab\u628a\u5b83\u63d0\u9ad8\u4e86 270% \u2713 \u21d2 \u6cbb\u7597\u91cf\u4e5f\u968f\u4e4b\u53d8\u5927 \u2713\uff09\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1408.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1408.json: the transformation heals her after an attack")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
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
 * 1408\uff1a\u300c\u53d8\u8eab\u671f\u95f4\u2026\u65bd\u653e\u653b\u51fb\u540e\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 20% \u7684\u751f\u547d\u503c\u300d (2026-10-02).
 *
 * <p>\u2b50 TWO-WAY, and the control is the SAME fight without the transformation. She is hurt first through the battle's own damage
 * entry point, so a heal has something to restore.
 */
public class TransformationHealsOnAttackTest {
    private static final int OWNER = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\u53d8\u8eab";

    /** \u2b50 Transformed, an attack heals her for a fifth of her (raised) Max HP. */
    @Test
    public void anAttackHealsTheTransformedHer() {
        double healed = healFromOneAttack(true);
        Assertions.assertEquals(0.20, healed, 1e-6,
                "\u300c\u56de\u590d\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650 20% \u7684\u751f\u547d\u503c\u300d (share of Max HP)");
    }

    /** \u26a0 Without the transformation the sentence has not started, so no attack heals her. */
    @Test
    public void withoutTheTransformationNothingIsHealed() {
        Assertions.assertEquals(0.0, healFromOneAttack(false), 1e-9,
                "\u300c\u53d8\u8eab\u671f\u95f4\u300d-- no transformation, no heal");
    }

    // ==================================================================

    /** the heal as a SHARE of Max HP, so the size of the pool does not matter. */
    private static double healFromOneAttack(boolean transform) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        if (transform) {
            Skill ult = owner.getSkills().get(SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: she has an ultimate");
            SkillExecutor.execute(battle, ult, owner, List.of(owner));
            battle.processRequests();
        }
        Assertions.assertEquals(transform, owner.getBuffManager().hasState(STATE),
                "precondition: the transformation is " + (transform ? "on" : "off"));

        battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, owner.getMaxHp() * 0.5);
        battle.processRequests();
        double hurt = owner.getCurrentHp();
        double maxHp = owner.getMaxHp();
        Assertions.assertTrue(hurt < maxHp, "precondition: she was really hurt (" + hurt + " of " + maxHp + ")");

        // \u26a0 Her BASIC attack, not her skill: the document says 卡厄斯兰那「拥有 1 个强化普攻和 2 个强化战技」, so the transformed
        // form attacks with the basic slot.
        Skill skill = owner.getSkills().get(SkillType.COMMON);
        Assertions.assertNotNull(skill, "precondition: she has a basic attack");
        SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return (owner.getCurrentHp() - hurt) / maxHp;
    }
}
''')
print("ok   judge written")
