package com.laosun.aluminium.test.content.characters.jade;

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
 * 1314 翡翠's trace 逆回购, the half the engine can already state: 「在敌方目标进入战斗时，翡翠获得 1 层【当品】」.
 *
 * <p>The pre-placed roster is covered by the BATTLE_START rule (that is "entered battle" too), so this judge is about a
 * LATER arrival: {@code ENEMY_ENTERED} adds one layer, and only then.
 */
public class JadeReverseRepoTest {

    private static final int LEVEL = 80;
    private static final int JADE = 1314;
    private static final String GOODS = "当品";

    @Test
    public void aNewEnemyPutsOneLayerOnHer() {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(jade, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        int atStart = jade.getBuffManager().stacksOf(GOODS);

        battle.addEnemy(BattleFixture.monster());
        int afterEntry = jade.getBuffManager().stacksOf(GOODS);
        System.out.println("[1314] goods " + atStart + " -> " + afterEntry);
        Assertions.assertEquals(atStart + 1, afterEntry, "「在敌方目标进入战斗时，翡翠获得 1 层【当品】」");

        battle.addEnemy(BattleFixture.monster());
        Assertions.assertEquals(afterEntry + 1, jade.getBuffManager().stacksOf(GOODS),
                "every entry pays, not only the first");
    }
}
