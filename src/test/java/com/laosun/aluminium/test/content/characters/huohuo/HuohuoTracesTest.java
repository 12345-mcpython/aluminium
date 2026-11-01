package com.laosun.aluminium.test.content.characters.huohuo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1217 藿藿's three 行迹 clauses (the attribute traces are folded in by the character builder; these three are rules).
 *
 * <ul>
 *   <li>不敢自专 - 「战斗开始时，藿藿获得【禳命】，持续 1 回合」</li>
 *   <li>贞凶之命 - 「抵抗控制类负面状态的概率提高 35%」, and only that class</li>
 *   <li>怯惧应激 - 「触发天赋为我方目标提供治疗时，藿藿恢复 1 点能量」 - the talent's heal hangs on TURN_START while
 *       【禳命】 holds, so the energy is paid there too</li>
 * </ul>
 */
public class HuohuoTracesTest {

    private static final int LEVEL = 80;
    private static final int HUOHUO = 1217;
    private static final String TALISMAN = "禳命";

    /**
     * The battle opens with 【禳命】 on her.
     */
    @Test
    public void theBattleOpensWithTheTalisman() {
        Fixture f = new Fixture();
        Assertions.assertTrue(f.huohuo.getBuffManager().hasState(TALISMAN),
                "「战斗开始时，藿藿获得【禳命】」");
    }

    /**
     * The trace resists the CONTROL class only.
     */
    @Test
    public void theTraceResistsControlOnly() {
        Fixture f = new Fixture();
        Assertions.assertEquals(0.35, f.huohuo.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                "「抵抗控制类负面状态的概率提高 35%」");
        Assertions.assertEquals(0.0, f.huohuo.getBuffManager().debuffResistOf(DebuffClass.DOT), 1e-9,
                "it must not become a general resistance");
    }

    /**
     * The talent's heal pays her one energy - and only while 【禳命】 holds.
     */
    @Test
    public void theTalentHealPaysOneEnergy() {
        Fixture f = new Fixture();
        double before = f.huohuo.getCurrentEnergy();
        f.fireTurnStart();
        Assertions.assertEquals(1.0, f.huohuo.getCurrentEnergy() - before, 1e-9,
                "「触发天赋…治疗时，藿藿恢复 1 点能量」");

        f.huohuo.getBuffManager().removeState(TALISMAN);
        double after = f.huohuo.getCurrentEnergy();
        f.fireTurnStart();
        Assertions.assertEquals(0.0, f.huohuo.getCurrentEnergy() - after, 1e-9,
                "no talent heal without the state, so no energy");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character huohuo;
        private final Battle battle;

        private Fixture() {
            huohuo = CharacterFactory.create(HUOHUO, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(huohuo, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void fireTurnStart() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, huohuo, huohuo, 0, 0));
        }
    }
}
