package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
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
        // 2026-10-19: 23039 ships no rules now - both stat shares are the cone's own properties (the health rule was a
        // duplicate of health_percent), and its payment clause is registered. The panel carries the two shares.
        for (int rank = 1; rank <= 5; rank++) {
            double plainHp = CharacterFactory.create(WEARER, LEVEL, true).getAttribute(AttributeType.HEALTH).get();
            double plainHeal = CharacterFactory.create(WEARER, LEVEL, true)
                    .getAttribute(AttributeType.HEAL_TAKEN_RATIO).get();
            var unit = wearer(23039, rank);
            Assertions.assertTrue(unit.getAttribute(AttributeType.HEALTH).get() > plainHp,
                    "rank " + rank + ": the HP share reaches the panel");
            Assertions.assertTrue(unit.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get() > plainHeal,
                    "rank " + rank + ": the healing-taken share reaches the panel");
            System.out.println("[23039] rank=" + rank + " both shares in the panel");
        }
    }
    }
