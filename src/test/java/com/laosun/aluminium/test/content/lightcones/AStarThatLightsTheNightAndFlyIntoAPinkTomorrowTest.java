package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Cones 23060 and 22006 (2026-09-30): clause 1 of each.
 *
 * <p>23060 is the instance-path spelling used CORRECTLY from the start: DEFENCE_IGNORE on DEALING_DAMAGE with
 * {@code instance: true}. Without that flag it would raise the WEARER's attribute, which is the defect 23031 shipped and
 * had to be repaired -- so this judge asserts the flag, and the sweep flips it.
 *
 * <p>22006: crit damage, permanent. Its other clauses are Trailblazer-Memory gated and registered.
 */
public class AStarThatLightsTheNightAndFlyIntoAPinkTomorrowTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1210;
    private static final double[] IGNORE = {0.32, 0.36, 0.40, 0.44, 0.48};
    private static final double[] CRITDMG = {0.12, 0.15, 0.18, 0.21, 0.24};

    private Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    @Test
    public void cone23060IgnoresDefenceOnTheHit() {
        for (int rank = 1; rank <= 5; rank++) {
            var rules = wearer(23060, rank).getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                    .filter(r -> r.id().equals("cone23060_def_ignore")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the ignore rule");
            var e = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", e.getOp(), "rank " + rank);
            Assertions.assertEquals("DEFENCE_IGNORE", e.getAttribute(), "rank " + rank);
            Assertions.assertEquals(IGNORE[rank - 1], e.getPercent(), 1e-9, "rank " + rank + ": the ratio");
            Assertions.assertEquals(Boolean.TRUE, e.getInstance(),
                    "rank " + rank + ": a property of THIS hit -- without it the WEARER's attribute moves");
            System.out.println("[23060] rank=" + rank + " ok");
        }
    }

    @Test
    public void cone22006StatesItsCritDamage() {
        for (int rank = 1; rank <= 5; rank++) {
            var rules = wearer(22006, rank).getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone22006_crit_damage")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the crit-damage rule");
            var e = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("CRIT_ATTACK", e.getAttribute(), "rank " + rank);
            Assertions.assertEquals(CRITDMG[rank - 1], e.getPercent(), 1e-9, "rank " + rank + ": the value");
            Assertions.assertEquals("self", e.getTarget(), "rank " + rank);
            System.out.println("[22006] rank=" + rank + " ok");
        }
    }
}
