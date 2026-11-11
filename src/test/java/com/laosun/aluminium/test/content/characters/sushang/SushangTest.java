package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1206 Sushang, from her own file: the talent that answers a weakness break, and the Sword Stance pair.
 *
 * <p>The talent is the interesting one: it hangs on the engine's `BREAK` event, which nothing had used yet. Its observable is her own
 * SPEED, so no damage comparison (and no level-convention confound) is involved.
 */
public class SushangTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;

    /**
     * Note: "当场上有敌方目标的弱点被击破": the BREAK event, which fires with the broken unit as its target.
     */
    @Test
    public void aWeaknessBreakRaisesHerSpeed() {
        Fixture f = new Fixture();
        double before = f.sushang.getAttribute(AttributeType.SPEED).get();

        f.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, f.sushang, f.enemy, 0, 0));

        Assertions.assertTrue(f.sushang.getAttribute(AttributeType.SPEED).get() > before,
                "\"when an enemy target's Weakness is Broken on the field, Sushang's SPD is raised by 20% for 2 turns\" (「当场上有敌方目标的弱点被击破，素裳的速度提高20%，持续2回合」): "
                        + before + " -> " + f.sushang.getAttribute(AttributeType.SPEED).get());
    }


    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
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
