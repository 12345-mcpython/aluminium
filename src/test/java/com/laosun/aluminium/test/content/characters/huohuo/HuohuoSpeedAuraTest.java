package com.laosun.aluminium.test.content.characters.huohuo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1217 藿藿's Eidolon 1, second half: 「当藿藿拥有【禳命】时我方全体提高 12% 的速度」.
 *
 * <p>The modifier sits on every ally while the GATE is on her: losing 【禳命】 drops the aura at the next turn boundary. Both
 * halves are measured on the same unit - with the buff up, then after the state leaves - so no cross-character base speed
 * enters the ratio.
 */
public class HuohuoSpeedAuraTest {

    private static final int LEVEL = 80;
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1001;
    private static final String TALISMAN = "禳命";

    @Test
    public void whileSheHoldsTheTalismanEveryoneIsFaster() {
        Fixture f = new Fixture(1);
        Assertions.assertTrue(f.huohuo.getBuffManager().hasState(TALISMAN), "the trace puts it on her at battle start");
        double allyUp = f.speed(f.ally);
        double herUp = f.speed(f.huohuo);

        f.dropTalismanAndTick();

        double allyDown = f.speed(f.ally);
        double herDown = f.speed(f.huohuo);
        System.out.println("[1217] ally " + allyDown + " -> " + allyUp + " ; her " + herDown + " -> " + herUp);
        Assertions.assertEquals(1.12, allyUp / allyDown, 1e-6, "「我方全体提高 12% 的速度」");
        // Her own panel carries other SPEED modifiers, so the percent lands on a different base than the bare ally: the
        // clause "includes her" is asserted as "she gets faster too", the exact 12% is pinned on the ally above.
        Assertions.assertTrue(herUp / herDown > 1.0, "「我方全体」 includes her");
    }

    @Test
    public void withoutTheEidolonThereIsNoAura() {
        Fixture f = new Fixture(0);
        double allyUp = f.speed(f.ally);
        f.dropTalismanAndTick();
        Assertions.assertEquals(allyUp, f.speed(f.ally), 1e-9, "E0: no aura either way");
    }

    private static final class Fixture {
        private final Character huohuo;
        private final Character ally;
        private final Battle battle;

        private Fixture(int eidolon) {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(huohuo, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        private double speed(Character unit) {
            return unit.getAttribute(AttributeType.SPEED).get();
        }

        private void dropTalismanAndTick() {
            huohuo.getBuffManager().removeState(TALISMAN);
            ally.getBuffManager().beforeMove();
            huohuo.getBuffManager().beforeMove();
        }
    }
}

