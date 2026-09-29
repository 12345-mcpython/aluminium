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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Three cones whose effects come from passive boosts: 21003 (attack, plus crit ONLY against two or fewer enemies), 21008 (effect hit rate and damage over
 * time) and 21007 (outgoing healing, plus party energy whenever the wearer casts a skill).
 *
 * <p>The enemy-count gate is a battle-start fact (unlike current health, which is why the 20003/20016 shape was withdrawn), so it can be driven: the same
 * fixture is built with one enemy and with three, and the crit clause is asserted to fire in the first and not in the second.
 */
public class ConePassiveGroupTest {
    private static final int WEARER = 1001;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21003GivesAttackAlwaysAndCritOnlyAgainstTwoOrFewer() {
        double base = base(21003, 1, AttributeType.ATTACK);
        Assertions.assertEquals(0.16 * base, gain(21003, 1, AttributeType.ATTACK, 1), 1e-3,
                "attack always, one enemy: base=" + base);
        Assertions.assertEquals(0.12, gain(21003, 1, AttributeType.CRIT_CHANCE, 1), 1e-6,
                "crit with one enemy");
        Assertions.assertEquals(0.0, gain(21003, 1, AttributeType.CRIT_CHANCE, 3), 1e-6,
                "and NO crit with three enemies -- the gate must hold");
    }

    @Test
    public void cone21008GivesEffectHitRateAndDotBoost() {
        Assertions.assertEquals(0.2, gain(21008, 1, AttributeType.EFFECT_HIT_RATE, 1), 1e-6, "effect hit rate");
        Assertions.assertEquals(0.24, gain(21008, 1, AttributeType.DOT_DAMAGE_BOOST, 1), 1e-6, "damage over time");
    }

    @Test
    public void cone21007GivesHealingAndPartyEnergyOnASkill() {
        Character unit = wearer(21007, 1);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy target = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit, ally), List.of(target), new Random(0));
        double healBoost = unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double allyEnergy = ally.getCurrentEnergy();
        battle.startBattle();
        Assertions.assertEquals(0.1, unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - healBoost, 1e-6,
                "the healing boost");
        battle.currentMove = new Signal(unit);
        battle.beforeMove();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, target, 0, 0);
        Assertions.assertTrue(ally.getCurrentEnergy() > allyEnergy,
                "the ally gains energy: " + allyEnergy + " -> " + ally.getCurrentEnergy());
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static double base(int cone, int rank, AttributeType attribute) {
        return wearer(cone, rank).getAttribute(attribute).baseValue();
    }

    private static double gain(int cone, int rank, AttributeType attribute, int enemies) {
        Character unit = wearer(cone, rank);
        List<Enemy> list = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            list.add(EnemyFactory.create(MONSTER, 90, 1));
        }
        Battle battle = new Battle(List.of(unit), list, new Random(0));
        double before = unit.getAttribute(attribute).get();
        battle.startBattle();
        return unit.getAttribute(attribute).get() - before;
    }
}
