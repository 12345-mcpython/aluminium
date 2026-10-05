package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * `amount_from_previous` gives the MAGNITUDE of what the previous effect moved (2026-10-02; reader: 114151"每消耗 1% 溢出值…").
 *
 * The two effects run in one rule, in order: a SPEND of , then a GAIN whose size is `amount_from_previous`. A spend arrives as a negative resource delta, so before this the
 * gain would have been handed -. Two-sided: the same pair with a GAIN first must still read a positive .
 */
public class SpentAmountIsPositiveTest {
    private static final int LEVEL = 80;
    private static final int SPARE = 1002;
    private static final int MONSTER = 1002011;
    private static final String SPENT = "被花掉";
    private static final String MARK = "探针数";

    private static TriggerTable table(boolean spendFirst) {
        EffectSpec spend = new EffectSpec();
        TriggerSpecs.set(spend, "op", "SPEND_RESOURCE");
        TriggerSpecs.set(spend, "resource", SPENT);
        TriggerSpecs.set(spend, "amount", 7.0);
        TriggerSpecs.set(spend, "target", "self");
        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(gain, "resource", MARK);
        TriggerSpecs.set(gain, "amountFromPrevious", Boolean.TRUE);
        TriggerSpecs.set(gain, "target", "self");
        EffectSpec plainGain = new EffectSpec();
        TriggerSpecs.set(plainGain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(plainGain, "resource", SPENT);
        TriggerSpecs.set(plainGain, "amount", 7.0);
        TriggerSpecs.set(plainGain, "target", "self");
        List<EffectSpec> doList = spendFirst ? List.of(spend, gain) : List.of(plainGain, gain);
        return new TriggerTable(SPARE, List.of(TriggerSpecs.rule("TURN_START", List.of(), doList.toArray(new EffectSpec[0]))));
    }

    @Test
    public void aSpendIsReadAsAPositiveMagnitude() {
        int afterSpend = run(true);
        int afterGain = run(false);
        System.out.println("[spent_amount] after a spend of 7 the follower gained " + afterSpend
                + " ; after a gain of 7 it gained " + afterGain);
        Assertions.assertEquals(7, afterSpend, "a spend of 7 must be handed over as +7, not -7");
        Assertions.assertEquals(7, afterGain, "and a gain of 7 still reads +7");
    }

    private static int run(boolean spendFirst) {
        Character spare = CharacterFactory.create(SPARE, LEVEL);
        Battle battle = new Battle(List.of(spare),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        spare = battle.characters.getFirst();
        // Room to grow AND something to spend: at the cap a gain credits nothing, and at zero a spend has nothing to take.
        spare.getResources().register(SPENT, 200, 100);
        spare.getResources().register(MARK, 9999, 0);
        spare.setTriggerTable(table(spendFirst));
        battle.fireTriggers(TriggerEvent.TURN_START, spare, null, 0, 0);
        battle.processRequests();
        return battle.characters.getFirst().getResources().value(MARK);
    }
}
