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
 * Three cones with a permanent attack boost and a kill-triggered second clause: 21019 (crit rate on a kill), 21020 (crit damage on a kill) and 21033 (heal a
 * share of the wearer's own attack on a kill). KILL is fired inside a driven turn and with a target.
 */
public class ConeKillGroupTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21019GivesAttackAndThenCritOnAKill() {
        Character unit = wearer(21019, 1);
        Enemy enemy = enemy();
        Battle battle = battle(unit, enemy);
        double baseAttack = unit.getAttribute(AttributeType.ATTACK).baseValue();
        double attackBefore = unit.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        double critBefore = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        Assertions.assertEquals(0.16 * baseAttack, unit.getAttribute(AttributeType.ATTACK).get() - attackBefore, 1e-3,
                "attack at battle start");
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.fireTriggers(TriggerEvent.KILL, unit, enemy, 0, 0);
        Assertions.assertEquals(0.12, unit.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore, 1e-6,
                "crit rate after a kill");
    }

    @Test
    public void cone21020GivesAttackAndThenCritDamageOnAKill() {
        Character unit = wearer(21020, 1);
        Enemy enemy = enemy();
        Battle battle = battle(unit, enemy);
        double critBefore = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.fireTriggers(TriggerEvent.KILL, unit, enemy, 0, 0);
        Assertions.assertEquals(0.24, unit.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore, 1e-6,
                "crit damage after a kill");
    }

    @Test
    public void cone21033HealsAShareOfAttackOnAKill() {
        Character unit = wearer(21033, 1);
        Enemy enemy = enemy();
        Battle battle = battle(unit, enemy);
        battle.startBattle();
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        unit.takeDamage(unit.getMaxHp() * 0.5);
        double before = unit.getCurrentHp();
        double attack = unit.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.KILL, unit, enemy, 0, 0);
        double healed = unit.getCurrentHp() - before;
        Assertions.assertEquals(0.12 * attack, healed, 1.0,
                "healed " + healed + " for an attack of " + attack);
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static Enemy enemy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }

    private static Battle battle(Character unit, Enemy enemy) {
        return new Battle(List.of(unit), List.of(enemy), new Random(0));
    }
}
