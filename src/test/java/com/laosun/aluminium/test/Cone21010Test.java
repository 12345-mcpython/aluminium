package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21010: hitting the SAME enemy again lifts the damage by 8% a time, up to five layers -- and another enemy starts
 * from nothing, because the layers live on the enemy.
 *
 * <p>\u2b50 The wearer is Dan Heng, NOT the character the earlier attempt used: that one carries a self-stacking combo mechanic of its
 * own (measured: 514 -> 828 -> 628 over three hits), which made every reading unreadable. The probe also settled the shape
 * itself: an ADD_STACK with `target: target` on a DEALING_DAMAGE event lands on the enemy (1/2/3 after three hits).
 */
public class Cone21010Test {
    private static final int CONE = 21010;
    private static final int WEARER = 1002;
    private static final int ALLY = 1003;
    private static final int LEVEL = 80;
    private static final int FIRST = 1002011;
    private static final String MARK = "\u775a\u51c6";

    private Character wearer;
    private Enemy first;
    private Enemy second;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        first = EnemyFactory.create(FIRST, 90, 1);
        second = EnemyFactory.create(FIRST, 90, 2);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(first, second),
                new Random(0));
        battle.startBattle();
    }

    private double hit(Enemy target) {
        return battle.applyDamage(target, new Damage(wearer, target, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void theLayersGrowOnTheSameEnemyAndStartOverOnAnother() {
        build(true);
        double one = hit(first);
        int afterOne = first.getBuffManager().stacksOf(MARK);
        hit(first);
        double three = hit(first);
        int afterThree = first.getBuffManager().stacksOf(MARK);
        double onSecond = hit(second);
        int secondStacks = second.getBuffManager().stacksOf(MARK);
        System.out.println("[21010] hit1=" + one + " (stacks " + afterOne + ") ; hit3=" + three + " (stacks "
                + afterThree + ") ; on the second enemy=" + onSecond + " (stacks " + secondStacks + ")");
        Assertions.assertEquals(1, afterOne, "the first hit marks the target");
        Assertions.assertEquals(3, afterThree, "the third hit has marked it three times");
        // \u2605 The instance boost is one layer per stack, so hit 3 must be (1 + 0.24) / (1 + 0.08) of hit 1.
        Assertions.assertEquals(1.24 / 1.08, three / one, 0.01, "each layer is 8% of the base, added up");
        Assertions.assertEquals(1, secondStacks, "a different enemy carries only its own first mark");
        Assertions.assertTrue(onSecond < three, "so it does not inherit the first enemy\u2019s layers");
    }

    @Test
    public void theLayersStopAtFive() {
        build(true);
        for (int i = 0; i < 8; i++) {
            hit(first);
        }
        int stacks = first.getBuffManager().stacksOf(MARK);
        System.out.println("[21010] after eight hits: stacks=" + stacks);
        Assertions.assertEquals(5, stacks, "\u6700\u591a\u53e0\u52a0 5 \u5c42");
    }

    @Test
    public void withoutTheConeNothingStacks() {
        build(false);
        hit(first);
        System.out.println("[21010] without the cone: stacks=" + first.getBuffManager().stacksOf(MARK));
        Assertions.assertEquals(0, first.getBuffManager().stacksOf(MARK), "no cone, no layers (false case)");
    }
}
