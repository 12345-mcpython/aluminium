package com.laosun.aluminium.test.engine;

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
 * <p>Each claim is asserted on its own fixture, so a mutation can only satisfy the test by matching the content: the FIRST event of a turn must grant (which
 * fails if `on_any` is missing), a SECOND event in the same turn must grant nothing (which fails if `per_turn` is missing), and the size of the gain is pinned
 * to the rank's own number.
 */
public class ConeAttackEnergyTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21004GivesTheRankShareOfBreakEffect() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21004, LEVEL, false, 1));
        Battle battle = new Battle(List.of(unit), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        double before = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.startBattle();
        Assertions.assertEquals(0.28, unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - before, 1e-6,
                "break effect at battle start");
    }

    @Test
    public void cone21004GrantsEnergyOncePerTurnAcrossEvents() {
        Assertions.assertEquals(4.0, firstEventGain(21004, TriggerEvent.SKILL_CAST), 1e-6,
                "a skill as the FIRST event of the turn must grant -- this is the on_any path");
        Assertions.assertEquals(0.0, secondEventGain(21004), 1e-6,
                "and a second event in the same turn must grant nothing");
    }

    @Test
    public void cone20012GrantsEnergyOncePerTurnAcrossEvents() {
        Assertions.assertEquals(4.0, firstEventGain(20012, TriggerEvent.BASIC_ATTACK), 1e-6, "after a basic attack");
        Assertions.assertEquals(4.0, firstEventGain(20012, TriggerEvent.SKILL_CAST), 1e-6, "after a skill, via on_any");
        Assertions.assertEquals(4.0, firstEventGain(20012, TriggerEvent.TAKING_HIT), 1e-6, "after being hit, via on_any");
        Assertions.assertEquals(0.0, secondEventGain(20012), 1e-6, "and only once per turn");
    }

    private static Character wearer(int cone) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
    }

    private static Battle turnBattle(Character unit, Enemy enemy) {
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        return battle;
    }

    private static double firstEventGain(int cone, TriggerEvent event) {
        Character unit = wearer(cone);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = turnBattle(unit, enemy);
        double before = unit.getCurrentEnergy();
        battle.fireTriggers(event, unit, enemy, 0, 0);
        return unit.getCurrentEnergy() - before;
    }

    private static double secondEventGain(int cone) {
        Character unit = wearer(cone);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = turnBattle(unit, enemy);
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        double afterFirst = unit.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        return unit.getCurrentEnergy() - afterFirst;
    }
}
