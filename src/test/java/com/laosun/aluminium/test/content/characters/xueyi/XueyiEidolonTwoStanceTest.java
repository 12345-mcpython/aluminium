package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1214 雪衣's Eidolon 2, the toughness half: 「天赋的追加攻击**无视弱点属性**削减敌方的韧性」.
 *
 * <p>Measured on a Quantum non-weakness enemy: without the Eidolon the follow-up takes nothing off, with it the bar drops
 * even though Quantum is not one of the target's weaknesses.
 */
public class XueyiEidolonTwoStanceTest {

    private static final int XUEYI = 1214;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void theFollowupIgnoresTheWeaknessGateAtEidolonTwo() {
        double without = stanceAfterFollowUp(0);
        double with = stanceAfterFollowUp(2);
        System.out.println("[1214] stance " + without + " -> " + with);
        // Both numbers include the ally's OWN 1 point (that is the reduction that triggers the talent), so the clause is
        // the DIFFERENCE: 3 hits x 15 points.
        Assertions.assertEquals(1.0, without, 1e-9, "below Eidolon 2 the follow-up takes nothing off");
        Assertions.assertEquals(45.0, with - without, 1e-9, "E2: 「无视弱点属性削减韧性」 = 3 段 x 15 点");
    }

    /** @return the enemy's stance right after the follow-up fired */
    private static double stanceAfterFollowUp(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);   // Thunder/Wind: Quantum is NOT a weakness
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double cap = 8;
        for (int i = 0; i < cap - 1; i++) {
            battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.THUNDER, 1, null);
            enemy.setStance(300);
        }
        double before = enemy.getStance();
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.THUNDER, 1, null);
        return before - enemy.getStance();
    }
}