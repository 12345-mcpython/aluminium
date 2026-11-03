package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1512 知更鸟•晴歌: Eidolon 2 — 「我方目标的全属性抗性穿透提高 18%。…气氛值上限提高 20 点」.
 *
 * <p>The two halves are different kinds of number, and that is the point: the penetration is a <b>value</b> on every ally, while the
 * 20 points are <b>capacity</b>. Writing the second as a gain would hand her 20 points instead of letting her hold them.
 */
public class RobinEidolonTwoTest {

    private static final int ROBIN = 1512;
    private static final int ALLY = 1002;

    @Test
    public void eidolon2GivesTheWholeSidePenetration() {
        Fixture e2 = new Fixture(2);
        Assertions.assertEquals(0.18, e2.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "「我方目标的全属性抗性穿透提高 18%」 — a teammate carries it too");

        Fixture e0 = new Fixture(0);
        Assertions.assertEquals(0.0, e0.ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get(), 1e-9,
                "control: nothing at E0");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character robin;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            robin = CharacterFactory.create(ROBIN, 80, false, null, null, eidolon);
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private int cap() {
            return robin.getResources().get("气氛值").getMax();
        }

        private int value() {
            return robin.getResources().value("气氛值");
        }
    }
}




