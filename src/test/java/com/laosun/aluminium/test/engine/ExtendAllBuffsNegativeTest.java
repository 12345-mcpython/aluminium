package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@code BuffManager.extendAllBuffs(-1)}: does the "all continuous effects lose a turn" spelling actually take ONE turn
 * off a 3-turn state, and does it remove a 1-turn one?
 *
 * <p>Asked because 1409 Hyacine (风堇)'s 忆灵技2 clause 「施放技能后使自身所有持续效果持续回合数减 1」 loads and fires, and
 * the state it shortened then could not be found at all - so either this call removes more than it should, or the rule
 * fires twice. This pins the call itself, with no content involved.
 */
public class ExtendAllBuffsNegativeTest {

    @Test
    public void oneTurnComesOffAThreeTurnState() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.getBuffManager().addBuff(new StateBuff("测试", 3));
        Assertions.assertEquals(3, unit.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                "precondition: three turns to start with");

        unit.getBuffManager().extendAllBuffs(-1);

        var buff = unit.getBuffManager().activeBuffs().getFirst();
        Assertions.assertEquals(2, buff.remainingTurns(),
                "「持续回合数减 1」 takes exactly one turn, not the whole state");
    }

    /**
     * Measured, and it is a property worth knowing: shortening a 1-turn state leaves it on the carrier with 0 turns
     * rather than taking it off. It disappears at the carrier's next tick, so the numbers are right - but `hasState`
     * still answers true in between, which is exactly the trap that made this clause look broken once.
     */
    @Test
    public void aOneTurnStateIsClampedAtZeroAndStaysUntilTheNextTick() {
        Character unit = CharacterFactory.create(1001, 80);
        unit.getBuffManager().addBuff(new StateBuff("测试", 1));

        unit.getBuffManager().extendAllBuffs(-1);

        Assertions.assertEquals(0, unit.getBuffManager().activeBuffs().getFirst().remainingTurns(),
                "the game's floor is 0 turns, not -1");
        Assertions.assertTrue(unit.getBuffManager().hasState("测试"),
                "a zero-turn state is NOT removed here -- it goes at the next tick");
    }
}
