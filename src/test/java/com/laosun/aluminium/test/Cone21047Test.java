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
 * Light cone 21047: 「击破特攻提高#1%。进入战斗时或造成击破伤害后，速度提高#2%，持续#3回合，该效果每回合只可触发1次」.
 *
 * <p>`on_any` carries the break path, and `per_turn: 1` is counted per rule and shared across events (round 137). So the test asserts both directions: a break on
 * its own grants (the positive check that a negative-only assertion cannot give), and a break in the same turn as the battle-start grant does not.
 */
public class Cone21047Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21047GivesBreakEffectAndSpeedAtBattleStart() {
        Character unit = wearer();
        Battle battle = battle(unit, EnemyFactory.create(MONSTER, 90, 1));
        double breakBefore = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        double speedBefore = unit.getAttribute(AttributeType.SPEED).get();
        battle.startBattle();
        Assertions.assertEquals(0.28, unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - breakBefore, 1e-6,
                "break effect at battle start");
        double base = unit.getAttribute(AttributeType.SPEED).baseValue();
        Assertions.assertTrue(unit.getAttribute(AttributeType.SPEED).get() - speedBefore > 0,
                "speed at battle start: base=" + base);
    }

    @Test
    public void cone21047GrantsSpeedOnABreakButOnlyOnceATurn() {
        Character unit = wearer();
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battle(unit, enemy);
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double beforeBlocked = unit.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        Assertions.assertEquals(0.0, unit.getAttribute(AttributeType.SPEED).get() - beforeBlocked, 1e-6,
                "the battle-start grant already used this turn's allowance, so a break grants nothing");
    }

    @Test
    public void cone21047GrantsSpeedOnABreakInAFreshBattle() {
        Character unit = wearer();
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = battle(unit, enemy);
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getAttribute(AttributeType.SPEED).get();
        double base = unit.getAttribute(AttributeType.SPEED).baseValue();
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        double gain = unit.getAttribute(AttributeType.SPEED).get() - before;
        Assertions.assertEquals(0.08 * base, gain, 1e-3,
                "a break grants speed through on_any: base=" + base + " gain=" + gain);
    }

    private static Character wearer() {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21047, LEVEL, false, 1));
    }

    private static Battle battle(Character unit, Enemy enemy) {
        return new Battle(List.of(unit), List.of(enemy), new Random(0));
    }
}
