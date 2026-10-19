package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Cones 23013 and 23039: the permanent attributes only -- and one refused trap.
 *
 * <p>23013: max HP +18..30% AND healing done +12..20%. The healing attribute is OUTGOING_HEALING_BOOST, the name seven
 * shipped files already use, and it is the right one here because the sentence is about the healing the wearer GIVES.
 *
 * <p>Note: 23039's other clause says "healing the wearer RECEIVES", which is the opposite direction; no shipped content names
 * such an attribute, so it is registered rather than fused onto the outgoing one.
 *
 * <p>2026-10-19: 23013's two shares are the cone's own {@code ability_property} values now (the stat rule was a duplicate of
 * them), so the panel is read against a cone-less wearer instead of the rule.
 */
public class TimeWaitsForNoOneAndFlameOfBloodBlazeMyPathTest {
    private static final int LEVEL = 80;
    private static final int WEARER = 1210;
    private static final double[] HP = {0.18, 0.21, 0.24, 0.27, 0.30};

    private Character wearer(int cone, int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, rank));
    }

    @Test
    public void cone23013StatesBothPermanentStats() {
        for (int rank = 1; rank <= 5; rank++) {
            double plainHp = CharacterFactory.create(WEARER, LEVEL, true).getAttribute(AttributeType.HEALTH).get();
            double plainHeal = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
            var wearerNow = wearer(23013, rank);
            Assertions.assertTrue(wearerNow.getAttribute(AttributeType.HEALTH).get() > plainHp,
                    "rank " + rank + ": the HP share reaches the panel");
            Assertions.assertTrue(wearerNow.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() > plainHeal,
                    "rank " + rank + ": the healing share reaches the panel");
            System.out.println("[23013] rank=" + rank + " both shares in the panel");
        }
    }

    @Test
    public void cone23039ShipsOnlyTheHealthClause() {
        for (int rank = 1; rank <= 5; rank++) {
            var rules = wearer(23039, rank).getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23039_health")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the HP rule");
            var e = rules.getFirst().effects();
            Assertions.assertEquals(1, e.size(),
                    "rank " + rank + ": ONLY the HP clause -- the incoming-healing one is registered");
            Assertions.assertEquals("HEALTH", e.getFirst().getAttribute());
            Assertions.assertEquals(HP[rank - 1], e.getFirst().getPercent(), 1e-9, "rank " + rank + ": HP");
            System.out.println("[23039] rank=" + rank + " ok");
        }
    }
}
