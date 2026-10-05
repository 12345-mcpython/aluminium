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
⚠ The crit is forced by the Random the tree itself uses for this (`return 0.0` = always crit; `return 1.0` = never crits,
as `AnchorDeathTest` puts it), so no assertion depends on a dice roll.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1412.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/PeerageCritDamageTest.java"
RULE = "peerage_skill_crit_damage"
PEERAGE = "爵位"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == RULE)]

rules.append({
    "on": "DEALING_DAMAGE",
    "id": RULE,
    "when": ["actor has_state " + PEERAGE, "from_category BPSKILL"],
    "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 0.72,
            "instance": True, "permanent": True, "target": "self"}],
    "source": ("1412 刻律德菈 行迹（文档 `:67`）：「持有【爵位】的角色…"
               "造成的**战技伤害的暴击伤害提高 72%**」"),
    "note": ("⭐ 2026-10-02：`CRIT_ATTACK` 就是暴击伤害 ✓（数据：`1412` 的 `crit_chance 0.05` ⾏ `crit_attack 0.5` ✓）；"
             "而 instance 路由**按名支持它** ✓（`ctx.damage().addCritDamage(...)` ✓）。"
             "“战技伤害的”由 `from_category BPSKILL` 表达 ✓。"),
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
 * 1412：「持有【爵位】的角色…造成的战技伤害的**暴击伤害提高 72%**」 (2026-10-02).
 *
 * <p>⭐ The assertion is about the CRIT MULTIPLIER, not about raw damage: the peerage also carries +16% DEF ignore, +10%
 * All-Type RES PEN and +20% pierce on skill damage, and all three raise crit and non-crit hits alike -- so dividing them out
 * leaves exactly what a +72% CRIT DMG changes. Both runs make the same six casts; the control removes only 【爵位】.
 *
 * <p>⚠ The crit is forced, not hoped for: the Random returns 0.0 for a crit and 1.0 for "never crits" (the idiom
 * `AnchorDeathTest` and `Cid1220FollowUpCritTest` use).
 */
public class PeerageCritDamageTest {
    private static final int OWNER = 1412;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String PEERAGE = "爵位";

    /** ⭐ The peer's skill crits harder than the same skill without the peerage. */
    @Test
    public void thePeerCritsHarderOnSkillDamage() {
        double withPeer = critRatio(true);
        double without = critRatio(false);
        Assertions.assertTrue(without > 1.0, "precondition: a crit really is bigger than a non-crit (" + without + ")");
        Assertions.assertTrue(withPeer > without,
                "「战技伤害的暴击伤害提高 72%」 (crit/non-crit: with=" + withPeer + ", without=" + without + ")");
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
