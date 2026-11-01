package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1218 椒丘's Eidolon 1, second clause: 「每当触发天赋效果使敌方目标陷入【烬煨】状态时，使本次叠加的【烬煨】层数额外提高 1 层」.
 *
 * <p>The Talent applies one layer per hit; with the Eidolon the same application carries two.
 */
public class JiaoqiuEidolonOneLayerTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    @Test
    public void theTalentAppliesTwoLayersWithTheEidolon() {
        Assertions.assertEquals(1, layersAfterOneHit(0), "without the Eidolon: 「施加 1 层」");
        Assertions.assertEquals(2, layersAfterOneHit(1), "with it: 「本次叠加的层数额外提高 1 层」");
    }

    private static int layersAfterOneHit(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        return enemy.getBuffManager().stacksOf(ASHEN);
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}