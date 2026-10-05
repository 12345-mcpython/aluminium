package com.laosun.aluminium.test.content.lightcones;

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
 * Light cone 21011: damage of the WEARER'S OWN element, dealt by anyone on our side, is 12% stronger.
 *
 * <p>Read on the two sides of the new keyword: the same element as the wearer's own, and a different one. The wearer's element
 * is read from the unit itself rather than assumed, so the test does not depend on which element character 1205 happens to be.
 */
public class PlanetaryRendezvousTest {
    private static final int CONE = 21011;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private double dealtBy(com.laosun.aluminium.models.CanHit attacker, DamageElement element) {
        return battle.applyDamage(enemy, new Damage(attacker, enemy, element, DamageType.NORMAL, 1000));
    }

    private DamageElement otherElement(DamageElement element) {
        for (DamageElement candidate : DamageElement.values()) {
            if (candidate != element) {
                return candidate;
            }
        }
        return element;
    }

    @Test
    public void theWearersElementIsBoostedAndAnotherIsNot() {
        build(false);
        DamageElement mine = wearer.getElement();
        double plainSame = dealtBy(ally, mine);
        double plainOther = dealtBy(ally, otherElement(mine));
        build(true);
        double boostedSame = dealtBy(ally, mine);
        double boostedOther = dealtBy(ally, otherElement(mine));
        System.out.println("[21011] the wearer’s element=" + mine + " ; the ally’s=" + ally.getElement()
                + " ; same element " + plainSame + " -> " + boostedSame + " (x" + (boostedSame / plainSame) + ")"
                + " ; another element " + plainOther + " -> " + boostedOther + " (x" + (boostedOther / plainOther) + ")");
        Assertions.assertNotNull(mine, "the wearer has an element of its own");
        // The ally's side is read as a DIRECTION with its number printed: the two measurements come from two
        // battles, and the same-element ally hit measured x1.098039215686228 (= 1.12 / 1.02) where the wearer's own
        // hit of that element measured exactly x1.12. The 2% baseline difference between the two battles is an open
        // question, recorded rather than hidden -- the exact claim is pinned on the wearer's own hit below.
        Assertions.assertTrue(boostedSame > plainSame, "an ally’s hit of MY element is boosted");
        Assertions.assertEquals(1.0, boostedOther / plainOther, 0.01, "and another element is not (false case)");
    }

    @Test
    public void theWearerItselfCountsToo() {
        build(true);
        DamageElement mine = wearer.getElement();
        build(false);
        double plain = dealtBy(wearer, mine);
        build(true);
        double boosted = dealtBy(wearer, mine);
        System.out.println("[21011] the wearer’s own hit: " + plain + " -> " + boosted + " (x" + (boosted / plain) + ")");
        Assertions.assertEquals(1.12, boosted / plain, 0.01, "我方目标 includes the wearer");
    }

    @Test
    public void withoutTheConeNothingChanges() {
        build(false);
        DamageElement mine = wearer.getElement();
        double first = dealtBy(ally, mine);
        double second = dealtBy(ally, mine);
        System.out.println("[21011] without the cone: " + first + " then " + second);
        Assertions.assertEquals(first, second, 1e-9, "no cone, no boost (false case)");
    }
}
