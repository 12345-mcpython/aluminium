package com.laosun.aluminium.test.content.characters.pearl;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1503 (Pearl): 感知容差 pays 5 per ally turn, at most 50 in a round, and the allowance resets on her turn.
 *
 * <p>「我方目标回合开始时，真珠获得 5 点【好活当赏】，最多获得 50 点【好活当赏】，真珠回合开始时重置可获得【好活当赏】数量」
 * (trace 1503102).
 *
 * <p>Why the allowance is not the same fact as the counter's cap, and why the reading has to say which one is doing the
 * limiting: her counter's own cap is 50 as well (from its declaration, 「持有上限为 50 点」), so a single reading of
 * "how many does she hold" cannot tell "the round is spent" from "the counter is full". The cases below separate them by
 * SPENDING the counter down between the gains, so the round's allowance is what stops the next gain.
 *
 * <p>And the reset's POSITION matters: `actor is_ally` counts Pearl herself, so on her own turn both clauses fire, and
 * file order decides whether her 5 points belong to the new round or the old one. The rule is placed before the gain
 * rule; this case is what makes that placement load-bearing.
 */
public class PearlRoundAllowanceTest {
    private static final int PEARL = 1503;
    private static final int ELATION_ALLY = 1502;
    private static final int HUNTER = 1204;
    private static final String COUNTER = "好活当赏";

    /**
     * Each ally turn pays 5, and the round stops paying once it has handed out 50.
     */
    @Test
    public void theRoundPaysFivePerTurnAndStopsAtFifty() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        drain(pearl);

        int total = 0;
        int turns = 0;
        // Well past ten turns, and each gain is spent back so the COUNTER's own cap of 50 cannot be what stops it.
        while (turns < 20) {
            allyTurn(battle, battle.characters().get(2));
            total += pearl.getResources().value(COUNTER);
            drain(pearl);
            turns++;
        }
        System.out.println("[pearl_allowance] over " + turns + " ally turns the round handed out " + total);
        Assertions.assertEquals(50, total,
                "「最多获得 50 点」: the round stops at fifty, however many turns it is given");
    }

    /**
     * Her own turn starts a new round: the allowance is available again.
     */
    @Test
    public void herOwnTurnStartsANewRound() {
        Battle battle = newBattle();
        Character pearl = battle.characters().getFirst();
        drain(pearl);

        int total = 0;
        for (int i = 0; i < 20; i++) {
            allyTurn(battle, battle.characters().get(2));
            total += pearl.getResources().value(COUNTER);
            drain(pearl);
        }
        Assertions.assertEquals(50, total, "precondition: the first round is spent");

        // Her turn: the reset runs first, so her own 5 points belong to the NEW round.
        battle.fireTriggers(TriggerEvent.TURN_START, pearl, pearl, 0, 0);
        battle.processRequests();
        int afterHerTurn = pearl.getResources().value(COUNTER);
        drain(pearl);
        // And the new round can hand out a further 45 on top of her own 5.
        int second = 0;
        for (int i = 0; i < 20; i++) {
            allyTurn(battle, battle.characters().get(2));
            second += pearl.getResources().value(COUNTER);
            drain(pearl);
        }
        System.out.println("[pearl_allowance] on her turn she gained " + afterHerTurn
                + " ; the new round then handed out " + second);
        Assertions.assertEquals(5, afterHerTurn,
                "her own turn pays its 5 from the round the reset just started");
        Assertions.assertEquals(45, second,
                "and the rest of the new round's 50 is still available");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static void allyTurn(Battle battle, Character actor) {
        battle.fireTriggers(TriggerEvent.TURN_START, actor, actor, 0, 0);
        battle.processRequests();
    }

    private static void drain(Character pearl) {
        pearl.getResources().spend(COUNTER, pearl.getResources().value(COUNTER));
    }

    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PEARL, 80), CharacterFactory.create(ELATION_ALLY, 80),
                        CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
