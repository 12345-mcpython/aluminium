package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Light cone 21024's first sentence: speed and damage up for the wearer from the start of the battle. */
public class Cone21024Test {
    private static final int CONE = 21024;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theWearerGainsSpeedAndDamage() {
        Battle baseline = battle(false);
        double speedBase = wearer.getAttribute(AttributeType.SPEED).get();
        double boostBase = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Battle battle = battle(true);
        double speedDelta = wearer.getAttribute(AttributeType.SPEED).get() - speedBase;
        double boostDelta = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - boostBase;
        System.out.println("[21024] speed +" + speedDelta + " damage boost +" + boostDelta);
        Assertions.assertTrue(speedDelta > 0, "speed rises");
        Assertions.assertTrue(boostDelta > 0, "and the damage boost too");
    }

    @Test
    public void theSpecPinsBothEffects() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(wearer, null, null, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone21024_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21024] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " target=" + effect.getTarget());
                // The SHARES, not just their existence: without this the `8/12 -> 4/6` mutation was 0 red (measured) --
                // `delta > 0` cannot tell 8% from 4%, which is discipline 192 again.
                if ("SPEED".equals(effect.getAttribute())) {
                    Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "speed 8% at rank 1");
                } else {
                    Assertions.assertEquals(0.12, effect.getPercent(), 1e-9, "damage 12% at rank 1");
                }
                Assertions.assertEquals("self", effect.getTarget(), "both are the wearer's own");
            }
        }
        Assertions.assertEquals(2, pinned, "speed and damage, one rule with two effects");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double speed = wearer.getAttribute(AttributeType.SPEED).get();
        double boost = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[21024] without the cone: speed=" + speed + " boost=" + boost);
        Assertions.assertEquals(speed, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9, "no cone, no change");
    }
}
