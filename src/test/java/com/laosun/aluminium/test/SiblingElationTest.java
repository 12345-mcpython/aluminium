package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8009 and 8010, the Elation Trailblazer pair (2026-09-29, round 198): the chosen ally's +50% CRIT DMG, for both ids.
 *
 * <p>The gain is asserted against a hand-built reference at percent 1.0 in the SAME pipeline, so the ratio 0.5 is the claim and the engine's own factors cancel —
 * the round-197 lesson, where asserting a share of a zero base compared nothing.
 */
public class SiblingElationTest {
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 The content's 0.5 against a reference 1.0, and the control, for BOTH ids. */
    @Test
    public void theUltimateRaisesTheChosenAllysCritDamage() {
        for (int cid : new int[]{8009, 8010}) {
            double content = ultimateGain(cid, 0);
            double reference = ultimateGain(cid, 1);

            Assertions.assertTrue(reference > 0, "cid " + cid + ": the reference must raise it at all");
            Assertions.assertEquals(0.5, content / reference, 0.05,
                    "cid " + cid + ": content " + content + " vs reference " + reference);
        }
    }

    /** \u26a0 「施放攻击后，固定恢复10点能量」 -- and the document's number, not just "some energy". */
    @Test
    public void theTalentGivesTenEnergyPerAttack() {
        for (int cid : new int[]{8009, 8010}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
            Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
            battle.startBattle();
            double before = tb.getCurrentEnergy();

            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, tb, enemy, 0, 0);

            Assertions.assertEquals(10.0, tb.getCurrentEnergy() - before, 1e-6,
                    "cid " + cid + ": \u300c\u65bd\u653e\u653b\u51fb\u540e\uff0c\u56fa\u5b9a\u6062\u590d10\u70b9\u80fd\u91cf\u300d");
        }
    }

    /** Census for both ids. */
    @Test
    public void theirFilesCarryTheClauses() {
        for (int cid : new int[]{8009, 8010}) {
            var table = com.laosun.aluminium.data.TriggerTables.of(cid);
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "cid " + cid);
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK), "cid " + cid);
            Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "cid " + cid);
        }
    }

    /** mode 0 = the shipped file, 1 = a hand-built 100% reference. Returns the chosen ally's CRIT DMG gain. */
    private static double ultimateGain(int cid, int mode) {
        Character tb = CharacterFactory.create(cid, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "CRIT_ATTACK");
            TriggerSpecs.set(effect, "percent", 1.0);
            TriggerSpecs.set(effect, "turns", 3);
            TriggerSpecs.set(effect, "target", "target");
            tb.setTriggerTable(new TriggerTable(cid, List.of(TriggerSpecs.rule(
                    TriggerEvent.ULT_CAST.name(), List.of("actor == self"), effect))));
        }
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, tb, ally, 0, 0);
        return ally.getAttribute(AttributeType.CRIT_ATTACK).get() - before;
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
