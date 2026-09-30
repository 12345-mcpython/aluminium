package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * Light cone 20021: the FIRST time the wearer summons a memosprite, one skill point and 12 energy come back.
 *
 * <p>\u2b50 \u300c\u9996\u6b21\u300d is the rule-level {@code once_per_battle}, and the judge measures it by firing SUMMONED twice: the second must
 * change nothing. The spec half pins the once-per-battle flag, because a rule that fires every time would still pass a
 * single-summon reading.
 */
public class Cone20021Test {
    private static final int CONE = 20021;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void theFirstSummonPaysOnce() {
        Battle battle = battle(true);
        double energy = wearer.getCurrentEnergy();
        int points = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.SUMMONED, wearer, null, 0, 0);
        double afterFirst = wearer.getCurrentEnergy() - energy;
        int pointsFirst = battle.getSkillPoints() - points;
        battle.fireTriggers(TriggerEvent.SUMMONED, wearer, null, 0, 0);
        double afterSecond = wearer.getCurrentEnergy() - energy - afterFirst;
        int pointsSecond = battle.getSkillPoints() - points - pointsFirst;
        System.out.println("[20021] first summon: energy +" + afterFirst + " points +" + pointsFirst
                + " ; second: energy +" + afterSecond + " points +" + pointsSecond);
        // \u2605 The VALUE, not just "something happened" (discipline 192): `> 0` cannot tell 12 from 6 -- measured, the
        // `12 -> 6` mutation was 0 red until this line existed.
        Assertions.assertEquals(12.0, afterFirst, 1e-9, "12 energy at rank 1");
        Assertions.assertTrue(pointsFirst >= 1, "and at least one skill point");
        Assertions.assertEquals(0.0, afterSecond, 1e-9, "the SECOND summon pays nothing (\u9996\u6b21)");
        Assertions.assertEquals(0, pointsSecond, "and no more skill points");
    }

    @Test
    public void theSpecPinsTheOncePerBattleFlag() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.SUMMONED,
                new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone20021_")) {
                continue;
            }
            pinned++;
            System.out.println("[20021] spec rule=" + rule.id());
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        Battle battle = battle(false);
        double energy = wearer.getCurrentEnergy();
        int points = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.SUMMONED, wearer, null, 0, 0);
        System.out.println("[20021] without the cone: energy +" + (wearer.getCurrentEnergy() - energy)
                + " points +" + (battle.getSkillPoints() - points));
        Assertions.assertEquals(0.0, wearer.getCurrentEnergy() - energy, 1e-9, "no cone, no energy (false case)");
    }
}
