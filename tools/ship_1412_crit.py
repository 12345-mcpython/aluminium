"""1412: 「持有【爵位】的角色造成的**战技伤害的暴击伤害提高 72%**」 (2026-10-02, item 32).

Document, verbatim (1412_刻律德菈.html:67): 「持有【爵位】的角色…造成的战技伤害的**暴击伤害提高 72%**、全属性抗性穿透提高 10.00%…」

WHY IT IS EXPRESSIBLE (measured, every piece):
  * `CRIT_ATTACK` IS 暴击伤害 -- the data says so: character_data.json has 1412 at `crit_chance: 0.05` and
    `crit_attack: 0.5` (a 50% base crit DMG), and 1101's shipped note reads 「提高等同于布洛妮娅 16.00% **暴击伤害** + 20.00%」
    for exactly that attribute;
  * the INSTANCE route supports it by name -- `TriggerInterpreter` has a branch `if (attribute == AttributeType.CRIT_ATTACK)`
    that calls `ctx.damage().addCritDamage(critDamage)`, i.e. "for THIS hit";
  * and `from_category BPSKILL` on `DEALING_DAMAGE` is what makes it 「**战技**伤害的」 -- the scope the sentence asks for,
    the same shape item 30 shipped.

THE JUDGE'S CLEAN ISOLATION (why it compares a RATIO): the peerage also carries +16% DEF ignore (item 22), +10% All-Type RES
PEN (item 29) and +20% pierce on skill damage (item 30). All three act on crit and non-crit hits ALIKE, so the ratio
"forced-crit damage / never-crit damage" cancels them -- and a +72% CRIT DMG is exactly what moves that ratio. Both runs use
the same six casts (so the same Charge and the same state), and the control removes only 【爵位】.
\u26a0 The crit is forced by the Random the tree itself uses for this (`return 0.0` = always crit; `return 1.0` = never crits,
as `AnchorDeathTest` puts it), so no assertion depends on a dice roll.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageCritDamageTest.java"
RULE = "peerage_skill_crit_damage"
PEERAGE = "\u7235\u4f4d"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "DEALING_DAMAGE",
    "id": RULE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.72,
            "instance": True, "permanent": True, "target": "self"}],
    "source": ("1412 \u523b\u5f8b\u5fb7\u83c8 \u884c\u8ff9\uff08\u6587\u6863 `:67`\uff09\uff1a\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272\u2026"
               "\u9020\u6210\u7684**\u6218\u6280\u4f24\u5bb3\u7684\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 72%**\u300d"),
    "note": ("\u2b50 2026-10-02\uff1a`CRIT_ATTACK` \u5c31\u662f\u66b4\u51fb\u4f24\u5bb3 \u2713\uff08\u6570\u636e\uff1a`1412` \u7684 `crit_chance 0.05` \u2f8f `crit_attack 0.5` \u2713\uff09\uff1b"
             "\u800c instance \u8def\u7531**\u6309\u540d\u652f\u6301\u5b83** \u2713\uff08`ctx.damage().addCritDamage(...)` \u2713\uff09\u3002"
             "\u201c\u6218\u6280\u4f24\u5bb3\u7684\u201d\u7531 `from_category BPSKILL` \u8868\u8fbe \u2713\u3002"),
})

if not isinstance(doc, dict):
    raise SystemExit("1412.json must be an object")
doc["rules"] = rules
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1412.json: the peerage's skill crit damage")

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
 * 1412\uff1a\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272\u2026\u9020\u6210\u7684\u6218\u6280\u4f24\u5bb3\u7684**\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 72%**\u300d (2026-10-02).
 *
 * <p>\u2b50 The assertion is about the CRIT MULTIPLIER, not about raw damage: the peerage also carries +16% DEF ignore, +10%
 * All-Type RES PEN and +20% pierce on skill damage, and all three raise crit and non-crit hits alike -- so dividing them out
 * leaves exactly what a +72% CRIT DMG changes. Both runs make the same six casts; the control removes only \u3010\u7235\u4f4d\u3011.
 *
 * <p>\u26a0 The crit is forced, not hoped for: the Random returns 0.0 for a crit and 1.0 for "never crits" (the idiom
 * `AnchorDeathTest` and `Cid1220FollowUpCritTest` use).
 */
public class PeerageCritDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "\u7235\u4f4d";

    /** \u2b50 The peer's skill crits harder than the same skill without the peerage. */
    @Test
    public void thePeerCritsHarderOnSkillDamage() {
        double withPeer = critRatio(true);
        double without = critRatio(false);
        Assertions.assertTrue(without > 1.0, "precondition: a crit really is bigger than a non-crit (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "\u300c\u6218\u6280\u4f24\u5bb3\u7684\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 72%\u300d (crit/non-crit: with=" + withPeer + ", without=" + without + ")");
    }

    /** forced-crit damage divided by never-crit damage, same scene, same six casts. */
    private static double critRatio(boolean keepPeerage) {
        double crit = damage(keepPeerage, true);
        double plain = damage(keepPeerage, false);
        Assertions.assertTrue(plain > 0, "precondition: the never-crit run deals damage");
        return crit / plain;
    }

    private static double damage(boolean keepPeerage, boolean forceCrit) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random() {
                    @Override
                    public double nextDouble() {
                        return forceCrit ? 0.0 : 1.0;
                    }
                });
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
