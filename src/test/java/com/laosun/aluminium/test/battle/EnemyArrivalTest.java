package com.laosun.aluminium.test.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An enemy can join the fight after it started.
 *
 * <p><b>Why.</b> A recorded fight states when each of its enemies first takes a hit - the 2026-10-30 capture brings nine of
 * them over six moments - and the share an AoE deals out depends on how many targets are on the field when it lands. The
 * enemy camp used to be fixed at construction (its view is unmodifiable), so a replay could only put them all down at turn
 * one, which is exactly the reading that disagreed with the capture.
 */
public class EnemyArrivalTest {

    private static Character attacker() {
        Character character = CharacterFactory.create(1001, 80);
        return character;
    }

    private static Enemy enemy(String name, double health) {
        Enemy enemy = Enemy.fromAttributes(name, health, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(health));
        enemy.heal(health);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * An enemy added mid-fight is part of the camp, and the one that was there keeps its place.
     */
    @Test
    public void anEnemyThatArrivesLaterJoinsTheCamp() {
        Battle battle = new Battle(List.of(attacker()), new ArrayList<>(List.of(enemy("first", 5000))), noCrit());
        battle.startBattle();
        Assertions.assertEquals(1, battle.enemyUnits().size(), "the fight starts with one enemy");

        Enemy latecomer = enemy("second", 4000);
        battle.addEnemy(latecomer);

        Assertions.assertEquals(2, battle.enemyUnits().size(), "the newcomer is in the camp");
        Assertions.assertSame(latecomer, battle.enemyUnits().get(1), "and it joined at the end");
        Assertions.assertEquals("first", battle.enemyUnits().get(0).getName(), "the first enemy did not move");
    }

    /**
     * The newcomer is a real target: a hit lands on it.
     */
    @Test
    public void theNewcomerCanBeHit() {
        Character character = attacker();
        Battle battle = new Battle(List.of(character), new ArrayList<>(List.of(enemy("first", 5000))), noCrit());
        battle.startBattle();
        Enemy latecomer = enemy("second", 4000);
        battle.addEnemy(latecomer);

        double before = latecomer.getCurrentHp();
        battle.castImmediate(character.getSkills().get(SkillType.COMMON), character, List.of(latecomer));

        Assertions.assertTrue(latecomer.getCurrentHp() < before,
                "the enemy that arrived later took the hit (" + before + " -> " + latecomer.getCurrentHp() + ")");
    }

    /**
     * Adding nothing is a no-op rather than a null in the camp.
     */
    @Test
    public void addingNullChangesNothing() {
        Battle battle = new Battle(List.of(attacker()), new ArrayList<>(List.of(enemy("first", 5000))), noCrit());
        battle.startBattle();
        battle.addEnemy(null);
        Assertions.assertEquals(1, battle.enemyUnits().size());
    }
}
