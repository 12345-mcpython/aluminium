package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1317 Rappa, from her own file (2026-09-29, round 209): the Charge that Weakness Break feeds, and the document's cap of 10.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): twelve breaks must still read ten.
 */
public class RappaTest {
    private static final int RAPPA = 1317;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 One Charge per Weakness Break, stopped at the document's ten. */
    @Test
    public void everyBreakAddsOneChargeUpToTen() {
        Character rappa = CharacterFactory.create(RAPPA, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(rappa), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, chargeOf(rappa), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.BREAK, rappa, enemy, 0, 0);
        Assertions.assertEquals(1, chargeOf(rappa),
                "\u300c\u6bcf\u5f53\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u88ab\u51fb\u7834\u65f6\uff0c\u4e71\u7834\u83b7\u5f971\u70b9\u5145\u80fd\u300d");

        for (int i = 0; i < 11; i++) {
            battle.fireTriggers(TriggerEvent.BREAK, rappa, enemy, 0, 0);
        }
        Assertions.assertEquals(10, chargeOf(rappa),
                "\u300c\u6700\u591a\u62e5\u670910\u70b9\u5145\u80fd\u300d -- twelve breaks must still read ten");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int chargeOf(Character rappa) {
        return rappa.getResources().get("\u5145\u80fd").getValue();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
