package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Two `on_any` readers: 21004 (break effect, plus energy after any attack, once per turn) and 20012 (energy after an attack or after being hit, once per turn).
 *
 * <p>The per-turn cap is shared across the events because they are the same compiled rule, so the test fires one attack, checks the gain, fires another of a
 * DIFFERENT kind (a skill) and checks that nothing more arrives -- which is exactly the claim 「单个回合内不可重复触发」 makes.
 */
public class ConeAttackEnergyTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21004GivesBreakEffectAndOneEnergyGainPerTurn() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21004, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double breakBefore = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.startBattle();
        Assertions.assertEquals(0.28, unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - breakBefore, 1e-6,
                "break effect at battle start");
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double energyBefore = unit.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        double afterBasic = unit.getCurrentEnergy();
        Assertions.assertEquals(4.0, afterBasic - energyBefore, 1e-6,
                "a basic attack grants energy: " + energyBefore + " -> " + afterBasic);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        Assertions.assertEquals(afterBasic, unit.getCurrentEnergy(), 1e-6,
                "per_turn: 1 is SHARED across the events, so a skill in the same turn grants nothing");
    }

    @Test
    public void cone20012GivesEnergyAfterAnAttackOrAfterBeingHit() {
        Assertions.assertTrue(gain(TriggerEvent.BASIC_ATTACK) > 0, "after a basic attack");
        Assertions.assertTrue(gain(TriggerEvent.SKILL_CAST) > 0, "after a skill, through on_any");
        Assertions.assertTrue(gain(TriggerEvent.TAKING_HIT) > 0, "after being hit, through on_any");
    }

    private static double gain(TriggerEvent event) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20012, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getCurrentEnergy();
        battle.fireTriggers(event, unit, enemy, 0, 0);
        return unit.getCurrentEnergy() - before;
    }
}
