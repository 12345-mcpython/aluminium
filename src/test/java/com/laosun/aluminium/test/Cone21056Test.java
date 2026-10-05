package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
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
 * Light cone 21056: at battle start the WHOLE party deals 16% more BREAK damage.
 *
 * <p>★ Read through the real break path: {@code Battle.reduceToughness} returns the break damage it settled, so the judge
 * compares a battle WITH the cone against one without -- the number a player would see, not an attribute value.
 */
public class Cone21056Test {
    private static final int CONE = 21056;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double SHARE = 0.16;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double breakDamage(Character attacker, Battle battle) {
        return battle.reduceToughness(attacker, enemy, DamageElement.FIRE, enemy.getMaxStance()).breakDamage();
    }

    @Test
    public void thePartyBreaksHarder() {
        Battle plain = battle(false);
        double withoutWearer = breakDamage(wearer, plain);
        plain = battle(false);
        double withoutAlly = breakDamage(ally, plain);

        Battle with = battle(true);
        System.out.println("[21056] attribute on the wearer=" + wearer.getAttribute(AttributeType.BREAK_DAMAGE_BOOST).get()
                + " on the ally=" + ally.getAttribute(AttributeType.BREAK_DAMAGE_BOOST).get());
        double withWearer = breakDamage(wearer, with);
        with = battle(true);
        double withAlly = breakDamage(ally, with);
        System.out.println("[21056] break damage wearer " + withoutWearer + " -> " + withWearer
                + " (x" + (withWearer / withoutWearer) + ") ; ally " + withoutAlly + " -> " + withAlly
                + " (x" + (withAlly / withoutAlly) + ")");
        Assertions.assertEquals(1 + SHARE, withWearer / withoutWearer, 0.02, "16% more break damage for the wearer");
        Assertions.assertEquals(1 + SHARE, withAlly / withoutAlly, 0.02, "and for the ally (我方全体)");
    }

    @Test
    public void theSpecPinsTheNewChannel() {
        battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START,
                new com.laosun.aluminium.models.TriggerTable.TriggerContext(wearer, null, null, 0, 0, null,
                        new Battle(List.of(wearer), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0)), null))) {
            if (!rule.id().startsWith("cone21056_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[21056] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " target=" + effect.getTarget());
                Assertions.assertEquals("BREAK_DAMAGE_BOOST", effect.getAttribute(), "the break channel, not a DMG boost");
                Assertions.assertEquals(0.16, effect.getPercent(), 1e-9, "16% at rank 1");
                Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }
}
