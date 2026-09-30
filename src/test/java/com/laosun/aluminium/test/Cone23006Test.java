package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23006: a speed stack per attack (duration-less, so the 21005 precedent applies) and a lightning DoT applied
 * only while the target is NOT already carrying it.
 */
public class Cone23006Test {
    private static final int CONE = 23006;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    @Test
    public void theSpeedStackGrowsPerAttackUpToItsCap() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double base = unit.getAttribute(AttributeType.SPEED).baseValue();
        double before = unit.getAttribute(AttributeType.SPEED).get();
        for (int i = 0; i < 4; i++) {
            battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        }
        double after = unit.getAttribute(AttributeType.SPEED).get();
        System.out.println("[23006] speed " + before + " -> " + after + " (delta=" + (after - before)
                + ", base*0.048*3=" + (base * 0.048 * 3) + ")");
        Assertions.assertEquals(base * 0.048 * 3, after - before, base * 0.02, "four attacks cap at three stacks");
    }

    /**
     * \u26a0 There is deliberately NO DoT judge here: the second clause of 23006 needs a NAMED damage-over-time to guard
     * on ("while the target is not already carrying 【游丝】"), and APPLY_DOT creates an unnamed one, so `has_state` can
     * never see it -- measured: with the guard in place the second hit stacked a second DoT. That clause is registered
     * instead of approximated.
     */
    @Test
    public void theDotClauseIsRegisteredRatherThanApproximated() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 230060));
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        int dots = enemy.getBuffManager().countBuffs(DotBuff.class);
        System.out.println("[23006] dots with the clause registered (expected 0): " + dots);
        Assertions.assertEquals(0, dots, "we do not approximate: no DoT rule is shipped");
    }
}
