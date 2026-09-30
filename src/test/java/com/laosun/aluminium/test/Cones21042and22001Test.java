package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Cones 21042 (break effect + post-ult crit) and 22001 (health + post-skill healing). */
public class Cones21042and22001Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21042RaisesBreakEffectThenCritAfterTheUlt() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21042, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double breakBefore = unit.getAttribute(AttributeType.BREAKING_EFFECT).get();
        battle.startBattle();
        double breakEffect = unit.getAttribute(AttributeType.BREAKING_EFFECT).get() - breakBefore;
        double critBefore = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double crit = unit.getAttribute(AttributeType.CRIT_CHANCE).get() - critBefore;
        System.out.println("[21042] breakEffect=" + breakEffect + " critAfterUlt=" + crit);
        Assertions.assertEquals(0.56, breakEffect, 1e-6, "rank 5 states 56% break effect");
        Assertions.assertEquals(0.3, crit, 1e-6, "rank 5 states 30% crit for two turns");
    }

    @Test
    public void cone22001RaisesHealthThenHealingAfterTheSkill() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(22001, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double healthBefore = unit.getAttribute(AttributeType.HEALTH).get();
        battle.startBattle();
        double health = unit.getAttribute(AttributeType.HEALTH).get() - healthBefore;
        double base = unit.getAttribute(AttributeType.HEALTH).baseValue();
        double healingBefore = unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double healing = unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - healingBefore;
        System.out.println("[22001] healthGain=" + health + " base=" + base + " healingAfterSkill=" + healing);
        Assertions.assertEquals(0.12 * base, health, 1e-6, "HEALTH is flat: the share scales the post-start base");
        Assertions.assertEquals(0.28, healing, 1e-6, "rank 5 states 28% healing for two turns");
    }
}
