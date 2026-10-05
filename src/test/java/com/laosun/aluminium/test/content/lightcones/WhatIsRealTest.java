package com.laosun.aluminium.test.content.lightcones;

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
 * Light cone 21035: "break effect up; after an attack, heal for a share of max HP plus a flat amount".
 *
 * <p>A TWO-TERM heal, so both terms are pinned against the unit's own max HP (never a hard-coded base), and each term gets its own mutation.
 */
public class WhatIsRealTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21035GivesBreakEffectAndBothHealTerms() {
        Assertions.assertEquals(0.24, breakEffect(1), 1e-6, "rank 1 break effect");
        Assertions.assertEquals(0.48, breakEffect(5), 1e-6, "rank 5 break effect");
        Assertions.assertEquals(0.02 * maxHp(1) + 800.0, healed(1), 1.0, "rank 1: share plus flat");
        Assertions.assertEquals(0.04 * maxHp(5) + 800.0, healed(5), 1.0, "rank 5: share plus flat");
    }

    private static Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21035, LEVEL, false, rank));
    }

    private static double maxHp(int rank) {
        return wearer(rank).getMaxHp();
    }

    private static Battle started(Character unit, Enemy enemy) {
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        return battle;
    }

    private static double breakEffect(int rank) {
        Character unit = wearer(rank);
        double before = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        started(unit, EnemyFactory.create(MONSTER, 90, 1));
        return unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - before;
    }

    private static double healed(int rank) {
        Character unit = wearer(rank);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = started(unit, enemy);
        unit.takeDamage(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        battle.fireTriggers(TriggerEvent.BASIC_ATTACK, unit, enemy, 0, 0);
        return unit.getCurrentHp() - before;
    }
}
