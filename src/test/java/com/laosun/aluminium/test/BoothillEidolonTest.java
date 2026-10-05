package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1315 波提欧's 星魂 1: 「波提欧造成伤害时无视敌方目标16%的防御力」.
 *
 * <p>An unconditional passive, so it is stated at the start of the battle rather than when a damage instance is handed over
 * (a modifier added there would be one step too late for the hit it describes). Two ways: rank 1 reads 16%, rank 0 nothing.
 */
public class BoothillEidolonTest {
    private static final int BOOTHILL = 1315;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;

    @Test
    public void hisFirstEidolonIgnoresSixteenPercentDefence() {
        Assertions.assertEquals(0.16, defenceIgnore(1), EPS,
                "波提欧造成伤害时无视敌方目标"
                        + "16%的防御力");
        Assertions.assertEquals(0.0, defenceIgnore(0), EPS, "rank 0 states nothing");
    }

    private static double defenceIgnore(int rank) {
        Character boothill = CharacterFactory.create(BOOTHILL, LEVEL, true, null, null, rank);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(boothill), List.of(enemy), new Random(0));
        battle.startBattle();
        return boothill.getAttribute(AttributeType.DEFENCE_IGNORE).get()
                - boothill.getAttribute(AttributeType.DEFENCE_IGNORE).baseValue();
    }
}
