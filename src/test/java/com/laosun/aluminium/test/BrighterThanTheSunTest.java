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
 * Light cone 23015: a basic attack grants one [龙吟] layer (two turns, at most two), and each layer raises ATTACK and
 * ENERGY REGENERATION RATE. The stack is added first in the same rule, so `per_stack` reads the CURRENT count.
 */
public class BrighterThanTheSunTest {
    private static final int CONE = 23015;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    private Battle battleWith(Character unit) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theRuleAddsTheStackBeforeTheTwoBonuses() {
        Character unit = wearer(true);
        int found = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BASIC_ATTACK,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            var effects = rule.effects();
            System.out.println("[23015] rule id=" + rule.id() + " ops=" + effects.stream().map(e -> e.getOp()).toList());
            Assertions.assertEquals("ADD_STACK", effects.get(0).getOp(), "the stack comes first");
            Assertions.assertEquals(2, effects.get(0).getMaxStacks(), "at most two layers");
            Assertions.assertEquals(2, effects.get(0).getTurns(), "for two turns");
            Assertions.assertEquals(0.18, effects.get(1).getPercent(), 1e-9, "18% attack per layer");
            Assertions.assertEquals("ENERGY_REGENERATION_RATE", effects.get(2).getAttribute(), "and energy regen");
            Assertions.assertEquals(0.06, effects.get(2).getPercent(), 1e-9, "6% per layer");
            found++;
        }
        Assertions.assertEquals(1, found, "exactly one such rule");
    }

    @Test
    public void oneBasicAttackGivesOneLayerAndFourCapAtTwo() {
        Character unit = wearer(true);
        Battle battle = battleWith(unit);
        double base = unit.getAttribute(AttributeType.ATTACK).baseValue();
        double before = unit.getAttribute(AttributeType.ATTACK).get();
        double erBefore = unit.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, unit, 0, 0);
        double afterOne = unit.getAttribute(AttributeType.ATTACK).get();
        double erAfterOne = unit.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        System.out.println("[23015] after one: attack " + before + " -> " + afterOne
                + " (delta=" + (afterOne - before) + ", base*0.18=" + (base * 0.18) + ")"
                + " ; energyRegen " + erBefore + " -> " + erAfterOne);
        Assertions.assertEquals(base * 0.18, afterOne - before, base * 0.02, "one layer is 18% of BASE attack");
        Assertions.assertEquals(0.06, erAfterOne - erBefore, 1e-9, "one layer is 6 points of energy regen");
        for (int i = 0; i < 3; i++) {
            battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, unit, 0, 0);
        }
        double afterFour = unit.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[23015] after four: delta=" + (afterFour - before) + " (base*0.36=" + (base * 0.36) + ")");
        Assertions.assertEquals(base * 0.36, afterFour - before, base * 0.02, "four attacks cap at two layers");
    }
}
