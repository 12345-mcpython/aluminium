package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
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
 * The write side: `Battle.castUltra` hands "what this cast spent" to the damage instance at settlement -- Note: it goes through a real cast, not a hand-set value.
 *
 * <p>Note: why a rule has to be built here at all: the read-side case hand-sets `castEnergySpent`, so it never goes through `Battle`; and it was measured that
 * commenting the write line out still leaves the full suite green (845 rounds).
 *
 * <p>Note: and "build a probe" is not free either: the ops that can read a custom `scale` are a closed set -- `MODIFY_ATTR` with
 * `cast_energy_spent` throws at load time (measured over 856 rounds), `ADD_STACK` silently does not read it (measured over 854 rounds:
 * the probe is always 2 layers, and stays 2 layers even with the write commented out). In fact only `BOOST_DAMAGE` honours it.
 *
 * <p>So the control is the same rule with the scale swapped: one with `scale: cast_energy_spent` (percent 0.02),
 * one without (percent 0.001). When the write happens the former is far higher; when the write is commented out the former reads 0, and so falls below the latter.
 */
public class Cone23062WriteSide2Test {
    private static final int LEVEL = 80;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.02;
    private static final double FLAT = 0.001;

    /** Casts one ultimate at full energy; a hand-built BOOST_DAMAGE rule reads what the instance spent. */
    private static double ultimateDamage(boolean scaled) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "BOOST_DAMAGE");
        if (scaled) {
            TriggerSpecs.set(boost, "scale", "cast_energy_spent");
            TriggerSpecs.set(boost, "percent", PER_POINT);
        } else {
            TriggerSpecs.set(boost, "percent", FLAT);
        }
        TriggerSpec rule = TriggerSpecs.rule(TriggerEvent.DEALING_DAMAGE.name(), List.of(), boost);
        TriggerSpecs.set(rule, "id", "probe_reads_cast_energy");
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(rule)));

        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double before = enemy.getCurrentHp();
        boolean cast = battle.castUltra(wearer, List.of(enemy));
        Assertions.assertTrue(cast, "the ultimate must be cast at full energy");
        return before - enemy.getCurrentHp();
    }

    @Test
    public void theSpendTheUltimateRecordedReachesTheInstance() {
        double scaled = ultimateDamage(true);
        double flat = ultimateDamage(false);
        System.out.println("[23062] write-side: scaled=" + scaled + " flat=" + flat
                + " (per point " + PER_POINT + ")");
        Assertions.assertTrue(scaled > flat,
                "with the write in place the scaled rule must beat the tiny flat one: " + scaled + " vs " + flat
                        + " -- with it commented out (round 845) the instance reads 0 and they swap");
    }
}
