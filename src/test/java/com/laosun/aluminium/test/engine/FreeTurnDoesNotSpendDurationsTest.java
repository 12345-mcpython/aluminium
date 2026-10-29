package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 「获得1个不消耗回合数的额外回合」 -- a free turn must spend no duration, an ordinary turn spends one.
 *
 * <p>Same 3-turn state, same unit, one turn each: ordinary -> 2, inserted (the game's TurnInsertAction, which 1409's
 * 忆灵技2 uses to make 小伊卡 act) -> 3.
 */
public class FreeTurnDoesNotSpendDurationsTest {

    @Test
    public void anOrdinaryTurnSpendsOneTurn() {
        Character unit = fastest();
        Battle battle = new Battle(List.of(unit), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        unit.getBuffManager().addBuff(new StateBuff("测试", 3));

        battle.stepForward();
        battle.afterMove();
        battle.processRequests();

        Assertions.assertEquals(2, unit.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                "an ordinary turn spends exactly one");
    }

    @Test
    public void anInsertedTurnSpendsNone() {
        Character unit = CharacterFactory.create(1001, 80);
        Battle battle = new Battle(List.of(unit), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        unit.getBuffManager().addBuff(new StateBuff("测试", 3));

        Assertions.assertTrue(battle.insertAction(unit), "precondition: the inserted action is accepted");
        battle.stepForward();
        battle.afterMove();
        battle.processRequests();

        Assertions.assertEquals(3, unit.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                "「不消耗回合数」: a free turn spends none");
    }

    /** A unit that is guaranteed to be first in the action order. */
    private static Character fastest() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.setAttribute(AttributeType.SPEED, new DoubleValue(9999));
        return unit;
    }
}
