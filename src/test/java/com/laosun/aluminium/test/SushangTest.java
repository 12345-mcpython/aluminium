package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1206 Sushang, from her own file (2026-09-29, round 155): the talent that answers a weakness break, and the Sword Stance pair.
 *
 * <p>The talent is the interesting one: it hangs on the engine's `BREAK` event, which nothing had used yet. Its observable is her own
 * SPEED, so no damage comparison (and no level-convention confound) is involved.
 */
public class SushangTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 「当场上有敌方目标的弱点被击破」: the BREAK event, which fires with the broken unit as its target. */
    @Test
    public void aWeaknessBreakRaisesHerSpeed() {
        Fixture f = new Fixture();
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();

        f.battle.fireTriggers(TriggerEvent.BREAK, f.sushang, f.enemy, 0, 0);

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.SPEED).get() > before,
                "\u300c\u5f53\u573a\u4e0a\u6709\u654c\u65b9\u76ee\u6807\u7684\u5f31\u70b9\u88ab\u51fb\u7834\uff0c\u7d20\u88f3\u7684\u901f\u5ea6\u63d0\u9ad820%\uff0c\u6301\u7eed2\u56de\u5408\u300d: "
                        + before + " -> " + f.sushang.getAttribute(AttributeType.SPEED).get());
    }

    /** Census: the talent, the Sword Stance pair, and the level convention. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(SUSHANG);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BREAK), "the talent");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.ALLY_ATTACK), "the chance half and the guaranteed half");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(sushang), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
