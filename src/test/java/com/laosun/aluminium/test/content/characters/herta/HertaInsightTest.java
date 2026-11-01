package com.laosun.aluminium.test.content.characters.herta;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1401 大黑塔's talent, first clause: 「敌方目标进入战斗时，大黑塔对其施加 1 层【解读】…最多可叠加 42 层」.
 *
 * <p>The layer lands on the unit that WALKED IN ({@code actor}), not on the caster and not on everybody - so the judge adds an
 * enemy after the battle is under way and checks that one.
 */
public class HertaInsightTest {

    private static final int LEVEL = 80;
    private static final int HERTA = 1401;
    private static final String INSIGHT = "解读";

    @Test
    public void anEnemyThatWalksInGetsOneLayer() {
        Character herta = CharacterFactory.create(HERTA, LEVEL);
        Enemy first = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(herta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(first)), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(0, first.getBuffManager().stacksOf(INSIGHT),
                "the roster at battle start is not 「进入战斗」 by this event's definition");

        Enemy late = BattleFixture.monster();
        battle.addEnemy(late);
        Assertions.assertEquals(1, late.getBuffManager().stacksOf(INSIGHT), "「对其施加 1 层【解读】」");

        Enemy another = BattleFixture.monster();
        battle.addEnemy(another);
        Assertions.assertEquals(1, another.getBuffManager().stacksOf(INSIGHT), "and every later entry pays too");
        Assertions.assertEquals(1, late.getBuffManager().stacksOf(INSIGHT), "the earlier one is not double-charged");
    }
}
