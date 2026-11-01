package com.laosun.aluminium.test.content.characters.gepard;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
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
 * 1104 杰帕德's Eidolon 2: 「冻结状态解除后，目标的速度降低 20%，持续 1 回合」.
 *
 * <p>Note: a state added by a rule is a POST-move buff, so its expiry is announced on {@code afterMove} -- the judge ticks
 * both halves and asserts on the result.
 */
public class GepardEidolonTwoTest {

    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;
    private static final String FREEZE = "冻结";

    @Test
    public void theEnemyIsSlowerOnceTheFreezeEnds() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(gepard, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = enemy.getAttribute(AttributeType.SPEED).get();

        enemy.getBuffManager().addBuff(new StateBuff(FREEZE, 1, false));
        enemy.getBuffManager().beforeMove();
        enemy.getBuffManager().afterMove();
        double after = enemy.getAttribute(AttributeType.SPEED).get();
        System.out.println("[1104] speed " + before + " -> " + after);
        Assertions.assertEquals(0.8, after / before, 1e-6, "「速度降低 20%」");
    }
}