package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23031 (2026-09-30): the DEF-ignore belongs to the HIT, not to the wearer.
 *
 * <p>⚠ PARTIAL ON PURPOSE, and the missing half is written down rather than implied. This asserts the SPELLING: every
 * DEFENCE_IGNORE effect on DEALING_DAMAGE for this cone carries {@code instance: true}, so it lands on the damage
 * instance and its {@code per_stack} is resolved by {@code perStackFactor} (which knows {@code self_stacks:}). It does
 * NOT yet assert the NUMBER the engine ends up using (0.27 x the 流光 stacks): that runtime judgement is still owed.
 *
 * <p>Why it exists: before 2026-09-30 this effect had no {@code instance}, so it raised the WEARER's attribute and its
 * {@code per_stack: self_stacks:流光} was read as {@code stacksOf("self_stacks:流光")} = 0 -- a multiplier that made the
 * whole bonus vanish. The spelling is the thing that was wrong, so the spelling is the thing this pins.
 */
public class Cone23031InstanceTest {
    private static final int CONE = 23031;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] IGNORE = {0.27, 0.30, 0.33, 0.36, 0.39};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierKeepsTheDefenceIgnoreOnTheHit() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();
            var rules = table.rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                    .filter(r -> r.id().equals("cone23031_ult_defence_ignore")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the DEF-ignore rule");
            Assertions.assertEquals(List.of("actor == self", "from_category ULTRA"),
                    rules.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only the wearer's own ultimate");
            var effect = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", effect.getOp());
            Assertions.assertEquals("DEFENCE_IGNORE", effect.getAttribute());
            Assertions.assertEquals(IGNORE[rank - 1], effect.getPercent(), 1e-9, "rank " + rank + ": the magnitude");
            Assertions.assertEquals("self_stacks:流光", effect.getPerStack(),
                    "rank " + rank + ": per stack of the cone's own counter");
            Assertions.assertEquals(Boolean.TRUE, effect.getInstance(),
                    "rank " + rank + ": a property of THIS hit -- without it the wearer's attribute moves and per_stack "
                            + "is read as stacksOf(\"self_stacks:...\") = 0, which is the bug this pins");
            System.out.println("[23031] rank=" + rank + " ok");
        }
    }
}
