package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1218 椒丘's Ultimate: 「将敌方目标具有的【烬煨】层数统一设置为场上【烬煨】层数的最高值」.
 *
 * <p>The op is {@code SET_STACKS} with the new {@code max_enemy_stacks:烬煨} scale: every enemy ends on the highest count
 * anyone had - the one below gains layers, the one above is untouched because it IS the maximum.
 */
public class JiaoqiuUnifyLayersTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    @Test
    public void theUltimateLevelsEveryEnemyToTheHighestCount() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy low = BattleFixture.monster();
        Enemy high = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(low, high)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        low.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        for (int i = 0; i < 3; i++) {
            high.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        }
        Assertions.assertEquals(1, low.getBuffManager().stacksOf(ASHEN), "precondition: 1 vs 3");

        // Fire the event itself (not the whole cast): the cast also fires the talent, which adds a layer to the primary target
        // afterwards and would blur what this clause does.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, jiaoqiu, low, 0, 0));
        System.out.println("[1218] low=" + low.getBuffManager().stacksOf(ASHEN)
                + " high=" + high.getBuffManager().stacksOf(ASHEN));
        Assertions.assertEquals(3, low.getBuffManager().stacksOf(ASHEN), "「统一设置为最高值」");
        Assertions.assertEquals(3, high.getBuffManager().stacksOf(ASHEN), "the maximum itself does not move");
    }
}
