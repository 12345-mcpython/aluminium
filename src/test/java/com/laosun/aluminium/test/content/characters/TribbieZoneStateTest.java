package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Tribbie's zone, as a state on him.
 *
 * <p>His ultimate (140303): "开启<b>结界</b>… <b>结界持续期间</b>，敌方目标受到的伤害提高 #2%… 结界持续 #4 回合，自身每回合开始时结界持续回合数减1。"
 *
 * <p>Our content already models the VULNERABILITY half as a debuff on the enemies, which is why "结界持续期间" had nothing to point at -- the zone was a
 * debuff, and no state said the zone was open. 1415's ode of passage names that state: "缇宝施放追加攻击触发<b>缇宝的结界的附加伤害</b>时…".
 *
 * <p>Two readings: the state is on him right after his ultimate, and it runs out on its own -- a state that never ends would satisfy the first half
 * while being wrong about the zone, which lasts #4 = 2 turns at every level of 140303.
 */
public class TribbieZoneStateTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int MONSTER = 1002011;
    private static final String ZONE = "结界";

    @Test
    public void theZoneIsAStateThatEndsOnItsOwn() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(tribbie, CharacterFactory.create(1002, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Assertions.assertFalse(tribbie.getBuffManager().hasState(ZONE),
                "precondition: no zone before the ultimate");

        tribbie.setCurrentEnergy(tribbie.getMaxEnergy());
        Assertions.assertTrue(battle.castUltra(tribbie, List.of(battle.enemies.getFirst())),
                "precondition: the ultimate can be cast");
        battle.processRequests();

        boolean afterUlt = tribbie.getBuffManager().hasState(ZONE);
        tribbie.afterMove(battle);
        tribbie.getBuffManager().afterMove();
        tribbie.afterMove(battle);
        tribbie.getBuffManager().afterMove();
        boolean afterTwoTurns = tribbie.getBuffManager().hasState(ZONE);
        System.out.println("[zone_state] right after the ultimate = " + afterUlt
                + " ; after two of his turns = " + afterTwoTurns);

        Assertions.assertTrue(afterUlt, "「开启【" + ZONE + "】」 (opens the zone) -- the state says the zone is open");
        Assertions.assertFalse(afterTwoTurns, "「【" + ZONE + "】持续 #4 回合」 (lasts #4 turns) -- #4 is 2 at every level, so it must end");
    }
}
