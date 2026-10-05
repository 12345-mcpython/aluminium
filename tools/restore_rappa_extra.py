"""Rappa's extra half, now that a judge CAN reach it (2026-10-02).

Last round withdrew 「若乱破当前攻击力高于2400点，每超过100点攻击力…最多额外提高8%」 because Rappa's Lv80 ATTACK is below 2400, so
the excess was 0 and removing `cap_amount` reddened nothing. The remedy was to register it, not to hope.

This round found the missing lever: `TriggerTable.plus(TriggerTable)` (:382), so a judge can APPEND to the file-driven
table -- her own rules stay, and one hand-built rule raises ATTACK past the threshold. So the rule comes back, and the
judge must bind BOTH halves: the base 2% (by selectivity, as before) and the excess with its 8% ceiling.

The ceiling is asserted at two excesses that both exceed 8% worth of points (800 and 1600 over), so a missing cap shows
up as two different numbers instead of one.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1317.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/BreakDamageTakenTest.java"
EXTRA_ID = "trace_break_damage_taken_up_above_attack"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == EXTRA_ID)]
rules.append({
    "on": "BREAK",
    "id": EXTRA_ID,
    "when": ["self_attr:ATTACK > 2400"],
    "do": [{
        "op": "MODIFY_DAMAGE_TAKEN",
        "damage_type": "break",
        "scale": "self_attr_above:ATTACK:2400",
        "percent": 0.0001,
        "cap_amount": 0.08,
        "turns": 2,
        "target": "target",
    }],
    "source": ("1317 乱破 行迹 忍法帖•枯叶（后半）: "
               "「若乱破当前攻击力高于 **2400** 点，**每超过 100 点攻击力**"
               "可使该数值额外提高 **1%**，**最多额外提高 8%**」"),
    "note": ("⭐ 上一轮曾因“判据摸不到”而撤下 ✓；本轮找到了杠杆 ✓："
             "`TriggerTable.plus(...)` ✓ ⇒ 判据可以**追加**一条手搭规则把攻击力抬过 2400 ✓。"
             "⭐ `scale: \"self_attr_above:ATTACK:2400\"` ✓（**每 100 点 1% = 每点 0.01%** ⇒ `percent: 0.0001` ✓）"
             "＋ `cap_amount: 0.08` ✓（“最多额外提高 8%”⇒ 对**最终值**的常数上限 ✓）。"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1317.json: both halves are back")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Rappa's trait (1317:440, 2026-10-02): 「受到的击破伤害提高2%，若当前攻击力高于2400点，每超过100点攻击力额外提高1%，
 * 最多额外提高8%」.
 *
 * <p>⭐ Two things this judge binds, and one trick it needs:
 * <ul>
 *   <li>SELECTIVITY -- `damage_type` is not validated at load, so the same base damage must move for BREAK and not for
 *       NORMAL; a silently ignored field would move both;</li>
 *   <li>the CEILING -- asserted at two excesses that each exceed 8% worth of points (800 and 1600 over the threshold),
 *       so a missing `cap_amount` shows up as two different numbers rather than one;</li>
 *   <li>the trick: her Lv80 ATTACK is BELOW 2400 (measured -- that is why the extra half could not be reached last
 *       round), so the judge APPENDS one hand-built rule via `TriggerTable.plus` and raises ATTACK with a flat modifier.
 *       Her file-driven table stays; nothing is replaced.</li>
 * </ul>
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** ⭐ Break damage is raised, normal damage is not. */
    @Test
    public void onlyBreakDamageIsRaised() {
        double breakDealt = dealt(DamageType.BREAK, 0);
        double normalDealt = dealt(DamageType.NORMAL, 0);
        Assertions.assertTrue(normalDealt > 0, "precondition: the hit lands (" + normalDealt + ")");
        Assertions.assertTrue(breakDealt > normalDealt * 1.005,
                "break damage must be raised by the rule (" + breakDealt + " vs " + normalDealt + ")");
    }

    /** ⭐ Past the threshold the bonus grows, and past 800 points over it stops at 8%. */
    @Test
    public void theExtraBonusStopsAtEightPercent() {
        double none = dealt(DamageType.BREAK, 0);
        double over800 = dealt(DamageType.BREAK, 3200);
        double over1600 = dealt(DamageType.BREAK, 4000);
        Assertions.assertTrue(over800 > none, "crossing the threshold must add something (" + none + " -> " + over800 + ")");
        Assertions.assertEquals(over800, over1600, over800 * 1e-6,
                "and 8% is the ceiling, so 1600 over reads the same as 800 (" + over800 + " vs " + over1600 + ")");
    }

    // ==================================================================

    private static double dealt(DamageType type, double flatAttack) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (flatAttack > 0) {
            EffectSpec boost = new EffectSpec();
            TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
            TriggerSpecs.set(boost, "attribute", "ATTACK");
            TriggerSpecs.set(boost, "amount", flatAttack);
            TriggerSpecs.set(boost, "permanent", true);
            TriggerSpecs.set(boost, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), boost)))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.IMAGINARY, type, 200));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
''')
print("ok   judge written (selectivity + ceiling)")
