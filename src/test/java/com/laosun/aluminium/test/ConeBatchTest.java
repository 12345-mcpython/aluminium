package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Three light cones authored in one batch from `weapons.json`: 20000 (crit rate at battle start), 20003 (defence, plus a low-health extra) and 20014
 * (speed after a kill).
 *
 * <p>Expectations follow the attribute's kind (round 9): a RATIO attribute (CRIT_CHANCE) takes the share as points, while FLAT attributes (DEFENCE, SPEED)
 * scale their base value. Each cone is asserted at two ranks, because a per-rank file whose ranks all held one value would otherwise pass unnoticed.
 */
public class ConeBatchTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone20000GivesCritRateAtBattleStart() {
        Assertions.assertEquals(0.12, critGain(20000, 1), 1e-6, "rank 1");
        Assertions.assertEquals(0.24, critGain(20000, 5), 1e-6, "rank 5");
    }



    @Test
    public void cone20014GivesSpeedAfterAKill() {
        Assertions.assertEquals(0.10, speedGainAfterKill(20014, 1) / baseSpeed(20014, 1), 1e-6, "rank 1, share of base");
        Assertions.assertEquals(0.18, speedGainAfterKill(20014, 5) / baseSpeed(20014, 5), 1e-6, "rank 5, share of base");
    }

    private static Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    private static Battle battleWith(Character wearer) {
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        return battle;
    }

    private static double critGain(int cone, int rank) {
        Character unit = wearer(cone, rank);
        Battle battle = battleWith(unit);
        double before = unit.getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.startBattle();
        return unit.getAttribute(AttributeType.CRIT_CHANCE).get() - before;
    }


    private static double baseSpeed(int cone, int rank) {
        return wearer(cone, rank).getAttribute(AttributeType.SPEED).baseValue();
    }




    private static double speedGainAfterKill(int cone, int rank) {
        Character unit = wearer(cone, rank);
        Battle battle = battleWith(unit);
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.SPEED).get();
        battle.fireTriggers(TriggerEvent.KILL, unit, null, 0, 0);
        return unit.getAttribute(AttributeType.SPEED).get() - before;
    }





}
