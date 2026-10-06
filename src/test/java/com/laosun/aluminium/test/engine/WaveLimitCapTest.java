package com.laosun.aluminium.test.engine;

import com.google.gson.Gson;
import com.laosun.aluminium.Battle;
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
 * "该效果每个波次最多触发 1 次".
 *
 * <p>Reader: 1506's warehouse skill. Two-way on purpose: twice in one wave fires once, and the NEXT wave fires again -- which is what
 * tells a wave cap apart from a battle-long one.
 *
 * <p>The rule comes through GSON, i.e. the path a data file takes, so `once_per_wave` is exercised as a DATA SPELLING (measured: it
 * parses). The reading is ENERGY rather than a panel: two firings that moved one attribute would evict each other.
 */
public class WaveLimitCapTest {
    private static final int OWNER = 1002;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-6;

    /** Twice in one wave = once; a new wave = once more. */
    @Test
    public void theCapIsPerWaveNotPerBattle() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        TriggerSpec rule = new Gson().fromJson("""
                {"on":"SKILL_CAST","when":["actor == self"],"once_per_wave":true,
                 "do":[{"op":"GAIN_ENERGY","amount":1,"target":"self"}]}
                """, TriggerSpec.class);
        Assertions.assertNotNull(rule, "precondition: the rule loads from the data spelling");
        Assertions.assertEquals(Boolean.TRUE, rule.getOncePerWave(),
                "precondition: the data spelling reaches the field");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule), List.of()));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.beginWave();

        double before = owner.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        double firstWave = owner.getCurrentEnergy() - before;

        double beforeSecond = owner.getCurrentEnergy();
        battle.beginWave();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, owner, null, 0, 0);
        double secondWave = owner.getCurrentEnergy() - beforeSecond;
        System.out.println("[wave-cap] two casts in wave 1 -> " + firstWave + " ; one more in wave 2 -> " + secondWave);

        Assertions.assertEquals(1.0, firstWave, EPS,
                "「每个波次最多触发 1 次」-- the second cast in the same wave changes nothing");
        Assertions.assertEquals(1.0, secondWave, EPS,
                "and the NEXT wave fires again, which is what tells a wave cap apart from a battle-long one");
    }
}
