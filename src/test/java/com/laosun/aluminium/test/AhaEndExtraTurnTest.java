package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
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
 * 1501："阿哈时刻结束时使火花获得 1 个[额外回合]" (2026-10-02).
 *
 * <p>FILE-DRIVEN, with the applier on the ALLY: 1501 has no Aha-moment creator of her own, and rebuilding HER table would
 * destroy the very reader under test. The ally lays the state on the whole camp instead, and the judge then ends it by hand.
 */
public class AhaEndExtraTurnTest {
    private static final int OWNER = 1501;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MOMENT = "阿哈时刻";

    /** Ending the moment owes her an extra turn. */
    @Test
    public void endingTheMomentGrantsAnExtraTurn() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        EffectSpec lay = new EffectSpec();
        TriggerSpecs.set(lay, "op", "APPLY_BUFF");
        TriggerSpecs.set(lay, "buff", MOMENT);
        TriggerSpecs.set(lay, "permanent", true);
        TriggerSpecs.set(lay, "target", "all_allies");
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule(TriggerEvent.SKILL_CAST.name(), List.of(), lay))));
        battle.fireTriggers(TriggerEvent.SKILL_CAST, ally, owner, 0, 0);
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(MOMENT), "precondition: the moment is on her");

        owner.getBuffManager().removeState(MOMENT);
        battle.processRequests();
        Assertions.assertSame(owner, battle.getExtraTurnActor(),
                "the reader must owe HER the extra turn");
    }
}
