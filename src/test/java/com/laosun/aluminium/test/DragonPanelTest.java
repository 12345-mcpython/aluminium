package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.SummonFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The dead dragon's panel (2026-10-02): speed 165 flat, health = 100% of the 【新蕊】 cap.
 *
 * <p>The first reader of the resource-based panel: the same spec yields a different health for a different resource
 * value, which is exactly what the sentence says.
 */
public class DragonPanelTest {
    /** ⭐ The panel is a share of a battle-level RESOURCE, and the flat speed rides beside it. */
    @Test
    public void theDragonPanelFollowsNewbud() {
        MemospriteSpec spec = Memosprites.of(1407);
        Assertions.assertNotNull(spec, "the dragon's spec must load from resources/memosprites/1407.json");
        Character master = CharacterFactory.create(1407, 80, false, null, null, 0);

        Summon small = SummonFactory.memosprite(master, spec, name -> 1000);
        Assertions.assertEquals(1000, small.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "100% of a 1000-point 【新蕊】");

        Summon large = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(34000, large.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "and 100% of a 34,000-point one -- the panel follows the resource");
        Assertions.assertEquals(165, large.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the document states 165 speed, flat");
    }
}
