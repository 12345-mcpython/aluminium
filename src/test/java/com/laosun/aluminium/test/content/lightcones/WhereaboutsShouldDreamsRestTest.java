package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23025: break damage collapses the target -- it then takes 24% more break damage from the wearer and is 20% slower.
 *
 * <p>Read with {@code stacksOf}, NOT {@code hasState}: this debuff is a stack, and the engine answers &quot;is that state on you&quot;
 * only for the state kind -- measured on one firing, {@code stacksOf=1} while {@code hasState=false}.
 */
public class WhereaboutsShouldDreamsRestTest {
    private static final int CONE = 23025;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COLLAPSE = "崩溃";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double breakDamage() {
        return battle.reduceToughness(wearer, enemy, DamageElement.FIRE, enemy.getMaxStance()).breakDamage();
    }

    @Test
    public void breakingCollapsesAndSlows() {
        build(true);
        double speedBefore = enemy.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.BREAK, wearer, enemy, 0, 0);
        int collapse = enemy.getBuffManager().stacksOf(COLLAPSE);
        double speedAfter = enemy.getAttribute(AttributeType.SPEED).get();
        System.out.println("[23025] collapse stacks=" + collapse + " (hasState="
                + enemy.getBuffManager().hasState(COLLAPSE) + ") ; speed " + speedBefore + " -> " + speedAfter);
        Assertions.assertEquals(1, collapse, "造成击破伤容时 collapses the target");
        // The AMOUNT, not just the direction (discipline 200): the enemy carries no other speed modifier, so the slow
        // is exactly a fifth of it. `speedAfter < speedBefore` survives a `20 -> 10 percent` mutation -- measured, 0 red.
        Assertions.assertEquals(0.8, speedAfter / speedBefore, 1e-9, "20% slower, as a share of its own speed");
    }

    @Test
    public void withoutTheConeNothingCollapses() {
        build(false);
        battle.fireTriggers(TriggerEvent.BREAK, wearer, enemy, 0, 0);
        System.out.println("[23025] without the cone: stacks=" + enemy.getBuffManager().stacksOf(COLLAPSE));
        Assertions.assertEquals(0, enemy.getBuffManager().stacksOf(COLLAPSE), "no cone, no collapse (false case)");
    }
}
