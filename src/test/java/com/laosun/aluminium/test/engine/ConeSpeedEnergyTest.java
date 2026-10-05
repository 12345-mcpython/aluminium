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
 * 21045 (break effect, plus speed after the ultimate) and 21048 (speed, plus energy for hitting a weakness-broken target, at most N times a turn).
 *
 * <p>Every assertion here is one that a mutation can break: the healthy case pins the state condition, the repeated hits pin the per-turn cap, and a fresh
 * fixture pins the `on_any` path positively (a negative-only claim cannot distinguish "capped" from "never bound").
 */
public class ConeSpeedEnergyTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21045GivesBreakEffectAndSpeedAfterTheUltimate() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21045, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double breakBefore = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.startBattle();
        Assertions.assertEquals(0.28, unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - breakBefore, 1e-6,
                "break effect at battle start");
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double speedBefore = unit.getAttribute(AttributeType.SPEED).get();
        double base = unit.getAttribute(AttributeType.SPEED).baseValue();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double gain = unit.getAttribute(AttributeType.SPEED).get() - speedBefore;
        Assertions.assertEquals(0.08 * base, gain, 1e-3,
                "speed after the ultimate: base=" + base + " gain=" + gain);
    }

    @Test
    public void cone21048GrantsEnergyOnlyAgainstABrokenTargetAndOnlySoOften() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21048, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        double before = unit.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        Assertions.assertEquals(0.0, unit.getCurrentEnergy() - before, 1e-6,
                "a HEALTHY target grants nothing -- the state condition must gate");
        battle.reduceToughness(unit, enemy, enemy.getStanceWeak().iterator().next(), 999);
        double afterBreak = unit.getCurrentEnergy();
        int hits = 2 + 2;
        for (int i = 0; i < hits; i++) {
            battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        }
        double granted = unit.getCurrentEnergy() - afterBreak;
        Assertions.assertEquals(3.0 * 2, granted, 1e-6,
                "of " + hits + " hits on a broken target, only " + 2 + " may grant: " + granted);
    }

    @Test
    public void cone21048AlsoGrantsThroughOnAny() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21048, LEVEL, false, 1));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.reduceToughness(unit, enemy, enemy.getStanceWeak().iterator().next(), 999);
        double before = unit.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        Assertions.assertEquals(3.0, unit.getCurrentEnergy() - before, 1e-6,
                "a skill as the FIRST event of the turn must grant -- this is the on_any path");
    }
}
