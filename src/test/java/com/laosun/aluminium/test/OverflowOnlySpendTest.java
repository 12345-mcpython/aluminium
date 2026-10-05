package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.ResourceSpec;
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
 * `SPEND_RESOURCE{overflow_only: true}` spends only the tier above the cap (2026-10-02; reader: 114151"召唤死龙时会消耗所有溢出[新蕊]").
 *
 * The judge tests the CAPABILITY, on a spare character whose hand-built table cannot disturb any loaded content, and with a resource it declares itself.
 */
public class OverflowOnlySpendTest {
    private static final int LEVEL = 80;
    private static final int SPARE = 1002;
    private static final int MONSTER = 1002011;
    private static final int MAX = 10;
    private static final int OVERFLOW = 4;
    private static final String MARK = "探针数";

    private static TriggerTable table() {
        EffectSpec spend = new EffectSpec();
        TriggerSpecs.set(spend, "op", "SPEND_RESOURCE");
        TriggerSpecs.set(spend, "resource", MARK);
        TriggerSpecs.set(spend, "overflowOnly", Boolean.TRUE);
        TriggerSpecs.set(spend, "target", "self");
        return new TriggerTable(SPARE, List.of(TriggerSpecs.rule("TURN_START", List.of(), spend)));
    }

    @Test
    public void onlyTheTierAboveTheCapIsSpent() {
        int[] full = run(MAX + OVERFLOW);
        int[] partial = run(MAX + 2);
        System.out.println("[overflow_spend] from " + (MAX + OVERFLOW) + " it leaves " + full[1]
                + " ; from " + (MAX + 2) + " it leaves " + partial[1]);
        Assertions.assertEquals(MAX, full[1], "everything above the cap is gone, and the cap itself is untouched");
        Assertions.assertEquals(MAX, partial[1], "and a partly used overflow is emptied too");
    }

    /** [the value before the spend, the value after] */
    private static int[] run(int start) {
        Character spare = CharacterFactory.create(SPARE, LEVEL);
        Battle battle = new Battle(List.of(spare),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        spare = battle.characters.getFirst();
        spare.getResources().register(MARK, MAX, 0, OVERFLOW);
        spare.getResources().gain(MARK, start);
        int before = spare.getResources().value(MARK);
        spare.setTriggerTable(table());
        battle.fireTriggers(TriggerEvent.TURN_START, spare, null, 0, 0);
        battle.processRequests();
        return new int[]{before, battle.characters.getFirst().getResources().value(MARK)};
    }
}
