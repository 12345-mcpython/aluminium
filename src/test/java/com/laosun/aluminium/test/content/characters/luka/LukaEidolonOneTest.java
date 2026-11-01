package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1111 卢卡's Eidolon 1: 「当卢卡行动时，若指定的敌方目标处于裂伤状态，卢卡造成的伤害提高 15%，持续 2 回合」.
 *
 * <p>The condition is the new {@code any_enemy has_state 裂伤} - the enemy camp, not the party - so the check turns on the
 * bleeding enemy and then asks whether his damage-taken boost moved, and off again for the control.
 */
public class LukaEidolonOneTest {

    private static final int LUKA = 1111;
    private static final int LEVEL = 80;

    @Test
    public void actingAgainstABleedingEnemyRaisesHisDamage() {
        Assertions.assertEquals(0.0, boostAfterTurn(1, false), 1e-9, "control: nothing while no enemy is bleeding");
        Assertions.assertEquals(0.15, boostAfterTurn(1, true), 1e-9, "「若指定的敌方目标处于裂伤…造成的伤害提高 15%」");
        Assertions.assertEquals(0.0, boostAfterTurn(0, true), 1e-9, "control: the Eidolon is what pays it");
    }

    /** His all-damage boost after his turn starts, with a bleeding enemy if asked. */
    private static double boostAfterTurn(int eidolon, boolean bleeding) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, eidolon);
        Character holder = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(luka, holder)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        if (bleeding) {
            enemy.getBuffManager().addBuff(new DotBuff(holder, DamageElement.PHYSICAL, 10, 3, 0, "裂伤"));
        }
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, luka, luka, 0, 0));
        return luka.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}