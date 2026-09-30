package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23014: a teammate being hit or paying health gives the wearer a layer of \u3010\u6708\u8680\u3011 (max 3); every layer lifts the
 * wearer's NEXT attack, and a full stack also ignores 12% of the target's defence. The stack is spent by attacking.
 */
public class Cone23014Test {
    private static final int CONE = 23014;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String MOON = "\u6708\u8680";
    private static final int CAP = 3;

    private Character wearer;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230141));
        return battle;
    }

    private double hit() {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    private void teammateIsHit() {
        battle.fireTriggers(TriggerEvent.TAKING_HIT, enemy, ally, 0, 0);
    }

    @Test
    public void aTeammatePayingGivesLayersUpToThree() {
        build(true);
        teammateIsHit();
        int one = wearer.getBuffManager().stacksOf(MOON);
        battle.fireTriggers(TriggerEvent.HP_CONSUMED, ally, null, 0, 0);
        int two = wearer.getBuffManager().stacksOf(MOON);
        teammateIsHit();
        teammateIsHit();
        int capped = wearer.getBuffManager().stacksOf(MOON);
        System.out.println("[23014] eclipse after hit=" + one + " then a health cost=" + two
                + " then two more hits=" + capped);
        Assertions.assertEquals(1, one, "a teammate being hit gives one layer");
        Assertions.assertEquals(2, two, "and a teammate paying health gives another");
        Assertions.assertEquals(CAP, capped, "\u6700\u591a\u53e0\u52a0 3 \u5c42");
    }

    @Test
    public void everyLayerLiftsTheNextAttack() {
        build(true);
        double none = hit();
        build(true);
        teammateIsHit();
        double one = hit();
        build(true);
        for (int i = 0; i < CAP; i++) {
            teammateIsHit();
        }
        double full = hit();
        System.out.println("[23014] damage none=" + none + " one layer=" + one + " full=" + full
                + " ; one layer x" + (one / none) + " ; full x" + (full / none));
        Assertions.assertEquals(1.14, one / none, 0.01, "one layer is +14%");
        Assertions.assertTrue(full > one, "and the full stack is worth more still (it also ignores defence)");
    }

    @Test
    public void attackingSpendsTheStack() {
        build(true);
        teammateIsHit();
        teammateIsHit();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 0);
        int after = wearer.getBuffManager().stacksOf(MOON);
        System.out.println("[23014] stacks after the wearer attacks=" + after);
        Assertions.assertEquals(0, after, "\u65bd\u653e\u653b\u51fb\u540e\u89e3\u9664");
    }

    @Test
    public void withoutTheConeNothingGathers() {
        build(false);
        teammateIsHit();
        System.out.println("[23014] without the cone: stacks=" + wearer.getBuffManager().stacksOf(MOON));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(MOON), "no cone, no stack (false case)");
    }

    @Test
    public void theSpecPinsTheIgnoreClause() {
        // \u2605 The one clause whose AMOUNT is not separable from the stack behaviourally (it rides the same instance as the
        // full-stack boost), so the number itself is pinned here.
        build(true);
        // \u2605\u2605 The rule exists only at a FULL stack, and `matching` evaluates conditions, so the state must be BUILT first --
        // measured: without this the loop found zero rules and pinned nothing, and `12 -> 6 percent` stayed 0 red.
        for (int i = 0; i < CAP; i++) {
            teammateIsHit();
        }
        Assertions.assertEquals(CAP, wearer.getBuffManager().stacksOf(MOON), "the stack is really full before matching");
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null,
                        battle, null))) {
            if (!rule.id().startsWith("cone23014_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                if (!"DEFENCE_IGNORE".equals(effect.getAttribute())) {
                    continue;
                }
                pinned++;
                System.out.println("[23014] spec " + rule.id() + " attribute=" + effect.getAttribute()
                        + " percent=" + effect.getPercent() + " instance=" + effect.getInstance());
                Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "12% at rank 1");
            }
        }
        Assertions.assertEquals(1, pinned, "at three layers the rule IS live -- the state is built above");
    }
}
