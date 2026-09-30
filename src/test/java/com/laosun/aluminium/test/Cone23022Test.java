package com.laosun.aluminium.test;

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
 * Light cone 23022: every damage-over-time TYPE the wearer strikes marks one layer of \u3010\u5148\u77e5\u3011, once per type per battle, up to four.
 *
 * <p>\u2b50 The DoTs are planted by NAME through {@code DotBuff}'s named constructor. The probe measured that a DotBuff planted with a
 * name answers {@code has_state} and {@code stacksOf} for exactly that name -- all four game names included.
 */
public class Cone23022Test {
    private static final int CONE = 23022;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String SEER = "\u5148\u77e5";
    private static final String WIND_NAME = "\u98ce\u5316";
    private static final String FIRE_NAME = "\u707c\u70e7";
    private static final String THUNDER_NAME = "\u89e6\u7535";
    private static final String PHYSICAL_NAME = "\u88c2\u4f24";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private void plant(String name, DamageElement element) {
        enemy.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.DotBuff(wearer, element, 50, 3, 0, name));
    }

    private void strike() {
        battle.fireTriggers(TriggerEvent.DEALING_DAMAGE, wearer, enemy, 0, 0);
    }

    @Test
    public void oneLayerPerDotTypeAndOnlyOnceEach() {
        build(true);
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        int afterBurn = wearer.getBuffManager().stacksOf(SEER);
        strike();
        int burnAgain = wearer.getBuffManager().stacksOf(SEER);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        int afterShock = wearer.getBuffManager().stacksOf(SEER);
        plant(WIND_NAME, DamageElement.WIND);
        plant(PHYSICAL_NAME, DamageElement.PHYSICAL);
        strike();
        int allFour = wearer.getBuffManager().stacksOf(SEER);
        System.out.println("[23022] seer: burn=" + afterBurn + " burnAgain=" + burnAgain
                + " +shock=" + afterShock + " +wind+physical=" + allFour);
        Assertions.assertEquals(1, afterBurn, "\u707c\u70e7 gives one layer");
        Assertions.assertEquals(1, burnAgain, "and repeating it gives nothing (once per type)");
        Assertions.assertEquals(2, afterShock, "\u89e6\u7535 adds a second");
        Assertions.assertEquals(4, allFour, "and all four types reach the cap of four");
    }

    @Test
    public void theLayersLiftAttack() {
        build(true);
        double before = wearer.getAttribute(AttributeType.ATTACK).get();
        plant(FIRE_NAME, DamageElement.FIRE);
        strike();
        double after = wearer.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[23022] attack " + before + " -> " + after);
        Assertions.assertTrue(after > before, "one layer is +5% attack");
    }

    @Test
    public void noDotNoLayer() {
        build(true);
        strike();
        System.out.println("[23022] without any DoT on the enemy: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "the gate needs a DoT state (false case)");
    }

    @Test
    public void withoutTheConeNoLayer() {
        build(false);
        plant(FIRE_NAME, DamageElement.FIRE);
        plant(THUNDER_NAME, DamageElement.THUNDER);
        strike();
        System.out.println("[23022] with DoTs but no cone: seer=" + wearer.getBuffManager().stacksOf(SEER));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SEER), "no cone, no layers (false case)");
    }
}
