"""Verify Rappa's break-damage-taken rule by DIFFERENCE, not by comparing damage types (2026-10-02).

What went wrong before: the judge compared BREAK damage against NORMAL damage and called the gap evidence. That gap is
intrinsic -- `DamageType.BREAK("break", crittable=false, boostable=false)` says break damage is not an ordinary boosted
hit at all, and the two types have different base maths. So the gap proved nothing about the rule.

The honest measurement is a difference between two runs that differ ONLY in whether her rule is present: build her, then
replace her table with one that keeps only an ATTACK raise (no rule), and compare the break damage dealt to the same
enemy. The difference is the rule's contribution, and `percent: 0.02` sits in exactly that difference.

This rewrites only the judge: the content (the base 2% rule, the extra half withdrawn) is untouched at HEAD.
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
 * <p>⭐ Measured by DIFFERENCE, between two runs that differ only in whether her rules are present. ⚠ An earlier version
 * compared BREAK damage against NORMAL damage; that gap is INTRINSIC (`DamageType.BREAK` is not boostable and the two
 * types have different base maths), so it proved nothing. The difference below is the rule's own contribution.
 */
public class BreakDamageTakenTest {
    private static final int OWNER = 1317;
    private static final int MONSTER = 1002011;

    /** ⭐ Her rule raises the break damage the enemy takes, and the raise is what the sentence states. */
    @Test
    public void herRuleRaisesBreakDamageTaken() {
        double withRules = dealt(true);
        double withoutRules = dealt(false);
        Assertions.assertTrue(withoutRules > 0, "precondition: the hit lands (" + withoutRules + ")");
        Assertions.assertTrue(withRules > withoutRules,
                "her rule must raise it (" + withoutRules + " -> " + withRules + ")");
        Assertions.assertEquals(0.02, withRules / withoutRules - 1, 5e-3,
                "and by the 2% the sentence states (measured " + (withRules / withoutRules - 1) + ")");
    }

    // ==================================================================

    /**
     * @param withHerRules when false, her own table is REPLACED by a rule-less one -- the only difference between the
     *                     two runs, so their gap is hers.
     */
    private static double dealt(boolean withHerRules) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (!withHerRules) {
            owner.setTriggerTable(new TriggerTable(OWNER, List.of()));
        }
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
print("ok   judge rewritten: difference, not damage-type comparison")
