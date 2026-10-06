package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Cone 23048 clause 3: the FIRST reader of the aimed unit that CAST_SETUP now carries.
 *
 * <p>"After the wearer casts a skill on a single ally, the TARGET's skill damage rises" needs one event that (a)
 * carries the category, (b) fires BEFORE settlement, and (c) names the ally. No event had all three: SKILL_CAST names
 * the ally but fires after and is refused by from_category; CAST_SETUP had (a) and (b) with a null target. So the
 * engine gained (c) and this test pins the resulting rule.
 *
 * <p>Note: target must stay `target` -- the ally the cast was aimed at. `self` would buff the wearer instead, which is
 * the wrong sentence, so the sweep flips it.
 */
public class EpochEtchedInGoldenBloodClause3Test {
    private static final int CONE = 23048;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] SKILL = {0.54, 0.675, 0.81, 0.945, 1.08};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesTheSkillDamageClause() {
        for (int rank = 1; rank <= 5; rank++) {
            var rules = wearer(rank).getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                    .filter(r -> r.id().equals("cone23048_skill_damage")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the clause");
            Assertions.assertEquals(List.of("from_category BPSKILL"),
                    rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": a SKILL cast");
            var e = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", e.getOp(), "rank " + rank);
            Assertions.assertEquals("SKILL_DAMAGE_BOOST", e.getAttribute(), "rank " + rank);
            Assertions.assertEquals(SKILL[rank - 1], e.getPercent(), 1e-9, "rank " + rank + ": the value");
            Assertions.assertEquals(3, e.getTurns(), "rank " + rank + ": three turns");
            Assertions.assertEquals("target", e.getTarget(),
                    "rank " + rank + ": the ALLY it was cast on -- the whole point of the engine change");
            System.out.println("[23048-3] rank=" + rank + " ok");
        }
    }
}
