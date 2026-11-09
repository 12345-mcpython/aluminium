package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Eidolon clauses "施放X后，&lt;attr&gt;提高N%，持续M回合", values verified against `eidolons.json`, expectations split by attribute kind.
 */
public class EidolonBatchTest {
    private static final int LEVEL = 80;

    @Test
    public void character1214Eidolon4GivesBREAKING_EFFECT() {
        Assertions.assertEquals(0.4, gain(1214, 4, TriggerEvent.ULT_CAST, AttributeType.BREAKING_EFFECT, false), 1e-6, "the data's own value");
    }

    @Test
    public void character1405Eidolon4GivesATTACK() {
        Assertions.assertEquals(0.3, gain(1405, 4, TriggerEvent.SKILL_CAST, AttributeType.ATTACK, true), 1e-6, "the data's own value");
    }

    @Test
    public void character1013Eidolon6GivesATTACK() {
        Assertions.assertEquals(0.25, gain(1013, 6, TriggerEvent.ULT_CAST, AttributeType.ATTACK, true), 1e-6, "the data's own value");
    }

    private static double gain(int cid, int eidolon, TriggerEvent event, AttributeType attribute, boolean flat) {
        Character unit = CharacterFactory.create(cid, LEVEL);
        // ⚠ The rank has to be SET: these clauses are 星魂 effects, and until 2026-11-09 the rules behind them had no
        // `min_eidolon` at all - so this test passed at E0, which is exactly what hid the bug.
        unit.setEidolonRank(eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.setCurrentMove(new Signal(unit));
        battle.beforeMove();
        double before = unit.getAttribute(attribute).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(event, unit, enemy, 0, 0));
        double gain = unit.getAttribute(attribute).get() - before;
        return flat ? gain / unit.getAttribute(attribute).baseValue() : gain;
    }
}