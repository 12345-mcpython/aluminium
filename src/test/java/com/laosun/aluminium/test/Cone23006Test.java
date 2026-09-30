package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
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
    /**
     * The 【游丝】 half: a marker state plus a Thunder DOT, guarded by "the target is not already carrying 【游丝】".
     * The guard only became writable once the state was modelled EXPLICITLY: a DOT's own document name is the element's
     * (Thunder -> 触电), which is a DIFFERENT state from this cone's 【游丝】.
     */
    @Test
    public void theThreadStateAndItsDotAreAppliedOnceAndGuarded() {
        // Spec: the DoT's scale and share, pinned so a magnitude change cannot slip through. Self-contained, so the
        // block's position within the method does not matter.
        int pinned = 0;
        Character specUnit = wearer(true);
        for (var rule : specUnit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(specUnit, specUnit, specUnit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("APPLY_DOT".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[23006] dot spec scale=" + effect.getScale() + " percent=" + effect.getPercent()
                            + " turns=" + effect.getTurns() + " element=" + effect.getElement());
                    Assertions.assertEquals("self_attr:ATTACK", effect.getScale(), "60% of the wearer's attack");
                    Assertions.assertEquals(0.6, effect.getPercent(), 1e-9, "rank 1 states 60%");
                    Assertions.assertEquals(1, effect.getTurns(),
                            "rank 1 states one turn; printing a value is not pinning it");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one DoT effect comes from this cone");
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        unit.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 230060));
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        int first = enemy.getBuffManager().countBuffs(DotBuff.class);
        boolean state = enemy.getBuffManager().hasState("游丝");
        System.out.println("[23006] dots after the first hit=" + first + " state=" + state);
        Assertions.assertEquals(1, first, "the first hit applies the DoT");
        Assertions.assertTrue(state, "and the marker state is on the enemy");
        battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        int second = enemy.getBuffManager().countBuffs(DotBuff.class);
        System.out.println("[23006] dots after a second hit=" + second);
        Assertions.assertEquals(1, second, "the guard stops a second application");
        Character bare = wearer(false);
        Enemy other = EnemyFactory.create(MONSTER, 90, 1);
        Battle plain = new Battle(List.of(bare), List.of(other), new Random(0));
        plain.startBattle();
        plain.applyDamage(other, new Damage(bare, other, DamageElement.PHYSICAL, DamageType.NORMAL, 10));
        System.out.println("[23006] dots without the cone=" + other.getBuffManager().countBuffs(DotBuff.class));
        Assertions.assertEquals(0, other.getBuffManager().countBuffs(DotBuff.class), "no cone, no state, no DoT");
    }
}
