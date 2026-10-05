"""Isolate Rappa's rule to measure it (2026-10-02), after two coarser attempts.

Attempt 1 compared BREAK vs NORMAL damage: intrinsic gap, proves nothing. Attempt 2 compared "her table" against "no
table at all": that gap is 50%, because dropping her table also drops her traces and level_convention -- far more than the
rule. `TriggerTable.plus` can add but not remove, so the honest isolation is a HAND-BUILT pair that differs in exactly one
number: the rule with `percent: 0.02` against the same rule with `percent: 0.0`. Their gap is the rule's own contribution.

This is the one case where hand-building is right rather than a smell: the object under test IS the rule, and the file's
rule is already written the same way (same op, same damage_type, same turns) -- what is being measured is the 2%.
ASCII only.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/BreakDamageTakenTest.java"

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
 * 「敌方目标的弱点被击破时，受到的击破伤害提高 2%」 (1317:440, 2026-10-02).
 *
 * <p>⭐ Isolated: two runs whose tables differ in exactly ONE number -- the rule's `percent` (0.02 vs 0.0) -- so the gap
 * between them is the 2% and nothing else. ⚠ Two coarser attempts failed first, and both are recorded in GAPS.md:
 * comparing BREAK against NORMAL proved nothing (the gap is intrinsic), and comparing her table against no table measured
 * 50% (dropping her table also drops her traces and level_convention).
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** ⭐ The rule's own contribution is the 2% the sentence states. */
    @Test
    public void theRuleContributesTwoPercent() {
        double on = dealt(0.02);
        double off = dealt(0.0);
        Assertions.assertTrue(off > 0, "precondition: the hit lands (" + off + ")");
        Assertions.assertTrue(on > off, "the rule must raise it (" + off + " -> " + on + ")");
        Assertions.assertEquals(0.02, on / off - 1, 1e-3,
                "and by 2% (measured " + (on / off - 1) + ")");
    }

    // ==================================================================

    /** The same rule, with only its `percent` differing between the two runs. */
    private static double dealt(double percent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec rule = new EffectSpec();
        TriggerSpecs.set(rule, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(rule, "damage_type", "break");
        TriggerSpecs.set(rule, "percent", percent);
        TriggerSpecs.set(rule, "turns", 2.0);
        TriggerSpecs.set(rule, "target", "target");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BREAK",
                List.of("actor == self"), rule))));

        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.IMAGINARY, DamageType.BREAK, 200));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
''')
print("ok   judge rewritten: isolated pair differing in one number")
