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

/**
 * Light cone 23008: after the wearer's ultimate the whole party gains a FLAT 12 speed for one turn ("提高 12 点", not 12%).
 */
public class Cone23008Test {
    private static final int CONE = 23008;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theSpecStatesAFlatAmountForTheWholeParty() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("SPEED".equals(effect.getAttribute())) {
                    pinned++;
                    System.out.println("[23008] spec amount=" + effect.getAmount() + " percent=" + effect.getPercent()
                            + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                    Assertions.assertEquals(12, effect.getAmount(), 1e-9, "a flat twelve points at rank 1");
                    Assertions.assertEquals(1, effect.getTurns(), "for one turn");
                    Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one speed rule from this cone");
    }

    @Test
    public void thePartyGainsTwelveSpeedAndABattleWithoutTheConeDoesNot() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double allyBefore = ally.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0);
        double allyAfter = ally.getAttribute(AttributeType.SPEED).get();
        System.out.println("[23008] ally with the cone in the party: " + allyBefore + " -> " + allyAfter);
        Assertions.assertEquals(allyBefore + 12, allyAfter, 1e-9, "the ally gains exactly twelve points");
        // \u26a0 The FALSE case cannot be a cone-less unit in the SAME party: the rule's target is `all_allies`, so every
        // ally benefits (measured: a cone-less wearer also gained 12). It has to be a battle with no cone at all.
        Character other = CharacterFactory.create(ALLY, LEVEL);
        Enemy second = EnemyFactory.create(MONSTER, 90, 1);
        Battle plain = new Battle(List.of(other), List.of(second), new Random(0));
        plain.startBattle();
        double before = other.getAttribute(AttributeType.SPEED).get();
        plain.fireTriggers(TriggerEvent.ULT_CAST, other, second, 0, 0);
        double after = other.getAttribute(AttributeType.SPEED).get();
        System.out.println("[23008] the same stimulus in a battle without the cone: " + before + " -> " + after);
        Assertions.assertEquals(before, after, 1e-9, "without the cone nothing happens");
    }
}
