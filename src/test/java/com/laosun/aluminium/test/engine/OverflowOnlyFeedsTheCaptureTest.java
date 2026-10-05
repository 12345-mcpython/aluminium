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

/** Does an `overflow_only` spend set the amount the next effect reads? */
public class OverflowOnlyFeedsTheCaptureTest {
    private static final int LEVEL = 80;
    private static final int SPARE = 1002;
    private static final int MONSTER = 1002011;
    private static final int MAX = 10;
    private static final int OVERFLOW = 4;
    private static final String SPENT = "被花掉";
    private static final String MARK = "探针数";

    private static TriggerTable table(boolean overflowOnly) {
        EffectSpec spend = new EffectSpec();
        TriggerSpecs.set(spend, "op", "SPEND_RESOURCE");
        TriggerSpecs.set(spend, "resource", SPENT);
        if (overflowOnly) {
            TriggerSpecs.set(spend, "overflowOnly", Boolean.TRUE);
        } else {
            TriggerSpecs.set(spend, "amount", 4.0);
        }
        TriggerSpecs.set(spend, "target", "self");
        EffectSpec gain = new EffectSpec();
        TriggerSpecs.set(gain, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(gain, "resource", MARK);
        TriggerSpecs.set(gain, "amountFromPrevious", Boolean.TRUE);
        TriggerSpecs.set(gain, "target", "self");
        return new TriggerTable(SPARE, List.of(TriggerSpecs.rule("TURN_START", List.of(), spend, gain)));
    }

    @Test
    public void theOverflowOnlySpendIsSeenByTheNextEffect() {
        int[] overflow = run(true);
        int[] stated = run(false);
        System.out.println("[overflow_capture] overflow_only: left " + overflow[1] + ", captured " + overflow[2]
                + " ; stated amount: left " + stated[1] + ", captured " + stated[2]);
        Assertions.assertEquals(MAX, overflow[1], "the overflow tier is what was spent");
        Assertions.assertEquals(OVERFLOW, overflow[2], "and the next effect must see exactly that");
    }

    /** [the value before, the value after, what the follower gained] */
    private static int[] run(boolean overflowOnly) {
        Character spare = CharacterFactory.create(SPARE, LEVEL);
        Battle battle = new Battle(List.of(spare),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        spare = battle.characters.getFirst();
        spare.getResources().register(SPENT, MAX, 0, OVERFLOW);
        spare.getResources().register(MARK, 9999, 0);
        spare.getResources().gain(SPENT, MAX + OVERFLOW);
        int before = spare.getResources().value(SPENT);
        spare.setTriggerTable(table(overflowOnly));
        battle.fireTriggers(TriggerEvent.TURN_START, spare, null, 0, 0);
        battle.processRequests();
        spare = battle.characters.getFirst();
        return new int[]{before, spare.getResources().value(SPENT), spare.getResources().value(MARK)};
    }
}
