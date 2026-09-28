package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1004 Welt, from his own file (2026-09-28, round 143): the ultimate's Imprison and the level convention.
 *
 * <p>\u26a0 The target is hand-made with no resistances on purpose: the project's fixture monster is immune to controls
 * (round 131 measured `STAT_CTRL_Frozen` at 1.0, which clamps the chance to 0). A 100% BASE chance still rolls, so an
 * unresisting target is what makes this deterministic.
 *
 * <p>Registered in his file: the bounce (an engine bug today), the speed-down roll, 【失重】, the 减速-gated talent, his
 * traces and eidolons.
 */
public class WeltTest {
    private static final int WELT = 1004;
    private static final int LEVEL = 80;

    @Test
    public void hisUltimateImprisonsEveryEnemy() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("禁锢"), "precondition: not imprisoned yet");

        f.battle.castImmediate(f.welt.getSkills().get(SkillType.ULTRA), f.welt, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("禁锢"),
                "\u300c\u6709100%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u9677\u5165\u7981\u9522\u72b6\u6001\uff0c\u6301\u7eed1\u56de\u5408\u300d");
    }

    @Test
    public void hisFileCarriesTheClauses() {
        var table = TriggerTables.of(WELT);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the imprison");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static final class Fixture {
        private final Character welt = CharacterFactory.create(WELT, LEVEL);
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle = new Battle(List.of(welt), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
